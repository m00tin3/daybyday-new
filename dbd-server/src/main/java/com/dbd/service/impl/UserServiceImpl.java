package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbd.common.BusinessException;
import com.dbd.common.PageResult;
import com.dbd.dto.UpdateProfileDTO;
import com.dbd.entity.Follow;
import com.dbd.entity.Post;
import com.dbd.entity.PostFavorite;
import com.dbd.entity.User;
import com.dbd.mapper.BarMapper;
import com.dbd.mapper.FollowMapper;
import com.dbd.mapper.PostFavoriteMapper;
import com.dbd.mapper.PostMapper;
import com.dbd.mapper.UserMapper;
import com.dbd.service.PostService;
import com.dbd.service.UserService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.utils.UserContext;
import com.dbd.vo.PostVO;
import com.dbd.vo.UserProfileVO;
import com.dbd.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现。Redis 技术点：
 * <ul>
 *   <li>签到日历：复用 BitMap {@code dbd:sign:{userId}:{yyyyMM}}，BITFIELD 一次取当月 31 位还原签到日</li>
 *   <li>粉丝计数：{@code dbd:user:fan:{userId}} INCR/DECR；关注关系以 follow 表（type=1）为准</li>
 * </ul>
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final FollowMapper followMapper;
    private final BarMapper barMapper;
    private final PostService postService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;

    public UserServiceImpl(UserMapper userMapper, PostMapper postMapper,
                           PostFavoriteMapper postFavoriteMapper, FollowMapper followMapper,
                           BarMapper barMapper, PostService postService,
                           StringRedisTemplate stringRedisTemplate, RedisIdWorker redisIdWorker) {
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.followMapper = followMapper;
        this.barMapper = barMapper;
        this.postService = postService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
    }

    /* ==================== 主页信息 ==================== */

    @Override
    public UserProfileVO profile(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setUser(UserVO.from(user));
        vo.setPostCount(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, id).in(Post::getStatus, 1, 2)));
        vo.setFollowerCount(fanCount(id));
        vo.setFollowingCount(followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, id).eq(Follow::getFollowType, 1)));
        Long current = UserContext.get();
        vo.setIsFollowed(current != null && !current.equals(id) && isFollowed(current, id));
        return vo;
    }

    @Override
    public PageResult<PostVO> posts(Long id, Integer page, Integer size) {
        // 参数依次为 barId / userId / city / keyword
        return postService.page(null, id, null, null, page, size);
    }

    /* ==================== 我的收藏 ==================== */

    @Override
    public PageResult<PostVO> favorites(Integer page, Integer size) {
        page = page == null || page < 1 ? 1 : page;
        size = size == null || size < 1 ? 10 : Math.min(size, 50);
        Long userId = UserContext.get();
        if (userId == null) {
            throw BusinessException.forbidden("请先登录");
        }
        // 收藏表分页 → 批量查帖子 + 作者 + 吧名组装（避免 N+1）
        IPage<PostFavorite> favPage = postFavoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .orderByDesc(PostFavorite::getCreatedAt));
        List<PostFavorite> favs = favPage.getRecords();
        if (favs.isEmpty()) {
            return PageResult.of(List.of(), 0L, page, size);
        }
        List<Long> postIds = favs.stream().map(PostFavorite::getPostId).distinct().toList();
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        Set<Long> userIds = postMap.values().stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = postMap.values().stream().map(Post::getBarId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, String> barNameMap = barIds.isEmpty() ? Map.of()
                : barMapper.selectBatchIds(barIds).stream()
                        .collect(Collectors.toMap(com.dbd.entity.Bar::getId, com.dbd.entity.Bar::getName));

        List<PostVO> list = new ArrayList<>();
        for (PostFavorite fav : favs) {
            Post post = postMap.get(fav.getPostId());
            // 统一可见性判定：隐藏帖(3)不出现在收藏列表
            if (post == null || !Post.isVisible(post.getStatus())) {
                continue;
            }
            User author = userMap.get(post.getUserId());
            list.add(PostVO.from(post, author, barNameMap.get(post.getBarId())));
        }
        return PageResult.of(list, favPage.getTotal(), page, size);
    }

    /* ==================== 签到日历 ==================== */

    @Override
    public Map<String, Object> signCalendar(Long userId, String month) {
        String ym = month == null || month.isBlank()
                ? LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                : month;
        String key = RedisKeyConstants.SIGN + userId + ":" + ym;

        // BITFIELD 一次取当月低 31 位（每天 1 位），还原出已签到的日期号。
        // 注意：BITFIELD 按 MSB-first 解释位（GET u31 0 的 bit0 为最高位），
        // 与 SETBIT 的 offset 相反，需按 offset k ↔ bit (30-k) 映射回日期。
        List<Long> bits = stringRedisTemplate.execute((RedisCallback<List<Long>>) conn -> conn.bitField(key.getBytes(),
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(31))
                        .valueAt(0)));
        List<Integer> signList = new ArrayList<>();
        long total = 0;
        long num = bits == null || bits.isEmpty() || bits.get(0) == null ? 0 : bits.get(0);
        for (int day = 1; day <= 31; day++) {
            int offset = day - 1;
            if (((num >> (30 - offset)) & 1L) == 1L) {
                signList.add(day);
                total++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("yearMonth", ym);
        result.put("signedDays", total);
        result.put("signList", signList);
        result.put("signCount", total);
        return result;
    }

    /* ==================== 关注用户 ==================== */

    @Override
    public Map<String, Object> follow(Long id) {
        Long current = UserContext.get();
        if (current == null) {
            throw BusinessException.forbidden("请先登录");
        }
        if (current.equals(id)) {
            throw BusinessException.forbidden("不能关注自己");
        }
        if (userMapper.selectById(id) == null) {
            throw BusinessException.notFound("用户不存在");
        }

        Follow existing = followMapper.selectOne(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, current)
                .eq(Follow::getFollowType, 1)
                .eq(Follow::getFollowUserId, id));
        String fanKey = RedisKeyConstants.USER_FAN + id;
        boolean followed;
        if (existing != null) {
            followMapper.deleteById(existing.getId());
            decreaseFanCount(fanKey);
            followed = false;
        } else {
            Follow follow = new Follow();
            follow.setId(redisIdWorker.nextId("follow"));
            follow.setUserId(current);
            follow.setFollowUserId(id);
            follow.setFollowType(1);
            followMapper.insert(follow);
            stringRedisTemplate.opsForValue().increment(fanKey);
            followed = true;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("isFollowed", followed);
        result.put("followerCount", fanCount(id));
        return result;
    }

    /* ==================== 个人资料 ==================== */

    @Override
    public UserVO updateProfile(UpdateProfileDTO dto) {
        Long userId = UserContext.get();
        if (userId == null) {
            throw BusinessException.forbidden("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        // 只写非 null 字段：MyBatis-Plus updateById 默认忽略 null（FieldStrategy.NOT_NULL），
        // 因此未提交的字段不会被覆盖
        User update = new User();
        update.setId(userId);

        if (dto.getNickname() != null) {
            String nickname = dto.getNickname().trim();
            if (nickname.isEmpty()) {
                throw BusinessException.param("昵称不能为空");
            }
            update.setNickname(nickname);
        }
        if (dto.getSignText() != null) {
            update.setSignText(dto.getSignText().trim());
        }
        if (dto.getIcon() != null) {
            update.setIcon(dto.getIcon().trim());
        }
        if (dto.getPhone() != null) {
            String phone = dto.getPhone().trim();
            if (!phone.equals(user.getPhone())) {
                // 登录账号全局唯一：先查冲突给出友好提示，再由 uk_phone 唯一索引兜底
                Long conflict = userMapper.selectCount(new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, phone)
                        .ne(User::getId, userId));
                if (conflict != null && conflict > 0) {
                    throw BusinessException.param("该账号已被占用，请换一个");
                }
                update.setPhone(phone);
            }
        }

        userMapper.updateById(update);
        return UserVO.from(userMapper.selectById(userId));
    }

    @Override
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = RedisKeyConstants.USER_ROLE + userId;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return User.ROLE_ADMIN == Integer.parseInt(cached);
        }
        User user = userMapper.selectById(userId);
        int role = user == null || user.getRole() == null ? User.ROLE_USER : user.getRole();
        stringRedisTemplate.opsForValue().set(key, String.valueOf(role), RedisKeyConstants.USER_ROLE_TTL);
        return role == User.ROLE_ADMIN;
    }

    /* ==================== 工具 ==================== */

    private boolean isFollowed(Long userId, Long targetId) {
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowType, 1)
                .eq(Follow::getFollowUserId, targetId)) > 0;
    }

    /** 粉丝数：Redis 计数为准，无值返回 0 */
    private long fanCount(Long userId) {
        String v = stringRedisTemplate.opsForValue().get(RedisKeyConstants.USER_FAN + userId);
        return v == null ? 0 : Long.parseLong(v);
    }

    /** 取消关注：计数大于 0 才递减（避免 Redis 无值时 DECR 变负数） */
    private void decreaseFanCount(String fanKey) {
        String v = stringRedisTemplate.opsForValue().get(fanKey);
        if (v == null) {
            return;
        }
        if (Long.parseLong(v) <= 0) {
            stringRedisTemplate.delete(fanKey);
        } else {
            stringRedisTemplate.opsForValue().decrement(fanKey);
        }
    }
}
