package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dbd.common.BusinessException;
import com.dbd.entity.Bar;
import com.dbd.entity.Follow;
import com.dbd.entity.Post;
import com.dbd.entity.User;
import com.dbd.mapper.BarMapper;
import com.dbd.mapper.FollowMapper;
import com.dbd.mapper.PostMapper;
import com.dbd.mapper.UserMapper;
import com.dbd.service.BadgeService;
import com.dbd.service.FeedService;
import com.dbd.service.PostCountService;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.utils.UserContext;
import com.dbd.vo.FeedResult;
import com.dbd.vo.PostVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注 Feed 流实现。Redis 技术点（对应 PROJECT_PLAN.md §5.1 模块 8）：
 * <ul>
 *   <li>推模式（写扩散）：发帖后把 postId 写入「关注该作者 / 关注该吧」的每个粉丝的
 *       {@code dbd:feed:user:{userId}} ZSet（score=发帖时间戳毫秒）</li>
 *   <li>滚动分页：{@code ZREVRANGEBYSCORE} + lastId 游标（排他区间避免重复），多取一条判断 hasMore</li>
 *   <li>懒构建兜底：feed 为空时从 follow 表拉取关注对象的近期帖子重建时间线</li>
 * </ul>
 */
@Slf4j
@Service
public class FeedServiceImpl implements FeedService {

    private final FollowMapper followMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final BarMapper barMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final BadgeService badgeService;
    /** 点赞/收藏计数回填（DB 那两列从来没被写过，真实值在 Redis Set 里） */
    private final PostCountService postCountService;

    public FeedServiceImpl(FollowMapper followMapper, PostMapper postMapper,
                           UserMapper userMapper, BarMapper barMapper,
                           StringRedisTemplate stringRedisTemplate,
                           BadgeService badgeService, PostCountService postCountService) {
        this.followMapper = followMapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.barMapper = barMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.badgeService = badgeService;
        this.postCountService = postCountService;
    }

    /* ==================== 时间线查询 ==================== */

    @Override
    public FeedResult feed(Long lastId, Integer size) {
        Long userId = UserContext.get();
        if (userId == null) {
            throw BusinessException.forbidden("请先登录");
        }
        size = size == null || size < 1 ? 10 : Math.min(size, 50);
        String key = RedisKeyConstants.FEED_USER + userId;

        // 首次访问懒构建：从关注关系拉取近期帖子重建时间线（老数据也能进 feed）
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(key))) {
            rebuildFeed(userId);
        }

        // ZREVRANGEBYSCORE：score 倒序；lastId 作为排他上限（score 为整数毫秒，-1 即开区间）
        double maxScore = lastId == null ? Double.MAX_VALUE : (double) (lastId - 1);
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxScore, 0, size + 1L);

        if (tuples == null || tuples.isEmpty()) {
            return new FeedResult(List.of(), lastId, false);
        }

        List<ZSetOperations.TypedTuple<String>> tupleList = new ArrayList<>(tuples);
        boolean hasMore = tupleList.size() > size;
        List<ZSetOperations.TypedTuple<String>> page = hasMore ? tupleList.subList(0, size) : tupleList;

        List<Long> postIds = page.stream().map(t -> Long.valueOf(t.getValue())).toList();
        List<PostVO> list = assemble(postIds);

        Long nextLastId = page.get(page.size() - 1).getScore() == null
                ? null : page.get(page.size() - 1).getScore().longValue();
        return new FeedResult(list, nextLastId, hasMore);
    }

    /* ==================== 写扩散 ==================== */

    @Override
    public void pushNewPost(Long postId, Long authorId, Long barId, double timestamp) {
        // 关注作者的粉丝 + 关注所属吧的粉丝，合并去重
        Set<Long> fanIds = new HashSet<>();
        List<Follow> userFans = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowType, 1).eq(Follow::getFollowUserId, authorId));
        for (Follow f : userFans) {
            fanIds.add(f.getUserId());
        }
        // 公告（barId 为 null）不属于任何吧，没有"吧粉丝"可推 —— 直接跳过，
        // 否则 MyBatis-Plus 会拼出 follow_bar_id = NULL 这种恒不命中的查询
        if (barId != null) {
            List<Follow> barFans = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getFollowType, 2).eq(Follow::getFollowBarId, barId));
            for (Follow f : barFans) {
                fanIds.add(f.getUserId());
            }
        }
        for (Long fanId : fanIds) {
            stringRedisTemplate.opsForZSet()
                    .add(RedisKeyConstants.FEED_USER + fanId, String.valueOf(postId), timestamp);
        }
        log.info("Feed 写扩散完成 postId={}, 推送给 {} 个粉丝", postId, fanIds.size());
    }

    /* ==================== 懒构建兜底 ==================== */

    private void rebuildFeed(Long userId) {
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId));
        List<Long> followUserIds = follows.stream()
                .filter(f -> f.getFollowType() != null && f.getFollowType() == 1 && f.getFollowUserId() != null)
                .map(Follow::getFollowUserId).distinct().toList();
        List<Long> followBarIds = follows.stream()
                .filter(f -> f.getFollowType() != null && f.getFollowType() == 2 && f.getFollowBarId() != null)
                .map(Follow::getFollowBarId).distinct().toList();
        if (followUserIds.isEmpty() && followBarIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Post::getStatus, 1, 2);
        wrapper.and(w -> {
            boolean first = true;
            if (!followUserIds.isEmpty()) {
                w.in(Post::getUserId, followUserIds);
                first = false;
            }
            if (!followBarIds.isEmpty()) {
                if (!first) {
                    w.or();
                }
                w.in(Post::getBarId, followBarIds);
            }
        });
        wrapper.orderByDesc(Post::getCreatedAt).last("LIMIT 200");

        List<Post> posts = postMapper.selectList(wrapper);
        for (Post p : posts) {
            stringRedisTemplate.opsForZSet()
                    .add(RedisKeyConstants.FEED_USER + userId, String.valueOf(p.getId()), toEpochMilli(p.getCreatedAt()));
        }
        log.info("Feed 懒构建完成 userId={}, 共 {} 帖", userId, posts.size());
    }

    /* ==================== 组装 ==================== */

    private List<PostVO> assemble(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        Set<Long> userIds = postMap.values().stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = postMap.values().stream().map(Post::getBarId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        // 只保留正常状态的吧：吧被隐藏后其下帖子不应出现在关注流
        Map<Long, String> barNameMap = barIds.isEmpty() ? Map.of()
                : barMapper.selectBatchIds(barIds).stream()
                        .filter(b -> b.getStatus() != null && b.getStatus() == 1)
                        .collect(Collectors.toMap(Bar::getId, Bar::getName));

        List<PostVO> list = new ArrayList<>();
        for (Long id : postIds) {
            Post post = postMap.get(id);
            // 统一可见性判定：隐藏帖(3)与已删除帖(0)都不能出现在关注流
            if (post == null || !Post.isVisible(post.getStatus())) {
                continue;
            }
            // 吧不可见（已隐藏/已删除）时同样跳过；
            // 公告（bar_id 为 NULL）不属于任何吧，不在 barNameMap 里，需放行而不是静默丢弃
            if (post.getBarId() != null && !barNameMap.containsKey(post.getBarId())) {
                continue;
            }
            list.add(PostVO.from(post, userMap.get(post.getUserId()),
                    post.getBarId() == null ? null : barNameMap.get(post.getBarId())));
        }
        // 关注流里同一作者可能连续出现多条，徽章批量查一次即可（内部按用户缓存）
        badgeService.fillPostAuthors(list);
        // 点赞/收藏数同样批量覆盖成 Redis 实时值（否则 PostCard 上恒显示 0 赞）
        postCountService.fill(list);
        return list;
    }

    private long toEpochMilli(LocalDateTime time) {
        return time == null ? System.currentTimeMillis()
                : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
