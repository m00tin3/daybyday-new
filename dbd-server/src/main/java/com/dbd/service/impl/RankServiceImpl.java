package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dbd.entity.Bar;
import com.dbd.entity.Post;
import com.dbd.entity.User;
import com.dbd.mapper.BarMapper;
import com.dbd.mapper.PostMapper;
import com.dbd.mapper.UserMapper;
import com.dbd.service.BadgeService;
import com.dbd.service.RankService;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.vo.PostVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 热帖榜实现：ZSet {@code dbd:rank:hot:post}，score=热度分（浏览 + 点赞*2 + 楼层*4）。
 * <p>点赞/浏览计数以 Redis 为准，重算时实时聚合（SCARD/GET）；楼层数 DB 已同步。
 * 定时每 5 分钟重算 + 首次访问懒构建。</p>
 */
@Slf4j
@Service
public class RankServiceImpl implements RankService {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final BarMapper barMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final BadgeService badgeService;

    public RankServiceImpl(PostMapper postMapper, UserMapper userMapper, BarMapper barMapper,
                           StringRedisTemplate stringRedisTemplate, BadgeService badgeService) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.barMapper = barMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.badgeService = badgeService;
    }

    @Override
    public List<PostVO> hotPosts() {
        Set<String> ids = stringRedisTemplate.opsForZSet()
                .reverseRange(RedisKeyConstants.RANK_HOT_POST, 0, 19);
        if (ids == null || ids.isEmpty()) {
            rebuildHotPostRank();
            ids = stringRedisTemplate.opsForZSet().reverseRange(RedisKeyConstants.RANK_HOT_POST, 0, 19);
        }
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = ids.stream().map(Long::valueOf).toList();
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        if (postMap.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = postMap.values().stream().map(Post::getUserId).distinct().toList();
        List<Long> barIds = postMap.values().stream().map(Post::getBarId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        // 只保留正常状态的吧：吧被隐藏后其下帖子不应出现在热帖榜
        Map<Long, String> barNameMap = barMapper.selectBatchIds(barIds).stream()
                .filter(b -> b.getStatus() != null && b.getStatus() == 1)
                .collect(Collectors.toMap(Bar::getId, Bar::getName));

        List<PostVO> result = new ArrayList<>();
        for (Long id : postIds) {
            Post post = postMap.get(id);
            // 统一可见性判定：隐藏帖(3)不能出现在热帖榜
            if (post == null || !Post.isVisible(post.getStatus())) {
                continue;
            }
            if (!barNameMap.containsKey(post.getBarId())) {
                continue;
            }
            result.add(PostVO.from(post, userMap.get(post.getUserId()), barNameMap.get(post.getBarId())));
        }
        badgeService.fillPostAuthors(result);
        return result;
    }

    /** 定时重算：热度分 = 浏览 + 点赞*2 + 楼层*4（点赞/浏览从 Redis 实时取） */
    @Scheduled(cron = "0 */5 * * * ?")
    @Override
    public void rebuildHotPostRank() {
        // 排除公告：热帖榜反映社区自发热度，官方公告不参与排名
        // （读侧 assemble 本来也会把公告过滤掉，这里顺手保证 ZSet 里不留无效成员）
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .in(Post::getStatus, 1, 2)
                .ne(Post::getType, Post.TYPE_ANNOUNCEMENT));
        for (Post post : posts) {
            Long views = getViewCount(post.getId());
            Long likes = getLikeCount(post.getId());
            int comments = post.getCommentCount() == null ? 0 : post.getCommentCount();
            double score = views + likes * 2 + comments * 4;
            stringRedisTemplate.opsForZSet()
                    .add(RedisKeyConstants.RANK_HOT_POST, String.valueOf(post.getId()), score);
        }
        log.info("热帖榜已重算，共 {} 帖", posts.size());
    }

    /** 浏览量：Redis INCR 计数为准（DB 为冷备） */
    private long getViewCount(Long postId) {
        String v = stringRedisTemplate.opsForValue().get(RedisKeyConstants.POST_VIEW + postId);
        return v == null ? 0 : Long.parseLong(v);
    }

    /** 点赞数：Set 容量为准 */
    private long getLikeCount(Long postId) {
        Long size = stringRedisTemplate.opsForSet().size(RedisKeyConstants.POST_LIKE + postId);
        return size == null ? 0 : size;
    }
}
