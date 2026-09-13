package com.dbd.service.impl;

import com.dbd.mapper.ActivityOrderMapper;
import com.dbd.service.BadgeService;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.vo.BadgeVO;
import com.dbd.vo.CommentVO;
import com.dbd.vo.PostVO;
import com.dbd.vo.UserVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 徽章服务实现。
 *
 * <p>缓存策略（对应 PROJECT_PLAN.md §5.1）：</p>
 * <ul>
 *   <li>key：{@code dbd:badge:user:{userId}} → BadgeVO 数组 JSON，TTL 10 分钟</li>
 *   <li><b>空值也缓存</b>（写成 {@code []}）：绝大多数用户没有徽章，不缓存空值的话
 *       每次列表渲染都会击穿到 DB</li>
 *   <li><b>批量防 N+1</b>：多个用户先并发读缓存，未命中的用户**合并成一次 IN 查询**，
 *       而不是每个用户一条 SQL</li>
 *   <li>一致性：领取成功后由订单落库方删除 key（Cache-Aside 失效模式）</li>
 * </ul>
 */
@Slf4j
@Service
public class BadgeServiceImpl implements BadgeService {

    private final ActivityOrderMapper activityOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BadgeServiceImpl(ActivityOrderMapper activityOrderMapper,
                            StringRedisTemplate stringRedisTemplate) {
        this.activityOrderMapper = activityOrderMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /* ==================== 徽章墙 ==================== */

    @Override
    public List<BadgeVO> userBadges(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<BadgeVO> badges = loadFromCache(userId);
        // userId 在单用户徽章墙里是冗余字段（调用方已经知道是谁），不回传
        badges.forEach(b -> b.setUserId(null));
        return badges;
    }

    /* ==================== 批量填充 ==================== */

    @Override
    public Map<Long, List<String>> badgeNamesOf(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 去重且保序：同一页里一个作者可能发了好几条，只该查一次
        Set<Long> distinct = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<BadgeVO>> byUser = new HashMap<>();
        List<Long> missed = new ArrayList<>(distinct.size());
        for (Long uid : distinct) {
            String json = stringRedisTemplate.opsForValue().get(RedisKeyConstants.BADGE_USER + uid);
            if (json == null) {
                missed.add(uid);
            } else {
                byUser.put(uid, parse(json));
            }
        }

        if (!missed.isEmpty()) {
            // 一次 IN 查询补齐所有未命中用户，避免 N+1
            List<BadgeVO> rows = activityOrderMapper.selectBadgesByUserIds(missed);
            Map<Long, List<BadgeVO>> grouped = rows.stream()
                    .filter(b -> b.getUserId() != null)
                    .collect(Collectors.groupingBy(BadgeVO::getUserId,
                            LinkedHashMap::new, Collectors.toList()));
            for (Long uid : missed) {
                // 无徽章的用户也要写空数组进缓存，否则每次都被击穿
                List<BadgeVO> badges = grouped.getOrDefault(uid, List.of());
                writeCache(uid, badges);
                byUser.put(uid, badges);
            }
        }

        Map<Long, List<String>> result = new HashMap<>(distinct.size());
        byUser.forEach((uid, badges) -> result.put(uid,
                badges.stream().map(BadgeVO::getBadgeName).filter(Objects::nonNull).toList()));
        return result;
    }

    @Override
    public void fillAuthor(UserVO author) {
        if (author == null || author.getId() == null) {
            return;
        }
        author.setBadges(badgeNamesOf(List.of(author.getId())).get(author.getId()));
    }

    @Override
    public void fillAuthors(Collection<UserVO> authors) {
        List<UserVO> targets = nonNullAuthors(authors);
        if (targets.isEmpty()) {
            return;
        }
        Map<Long, List<String>> map = badgeNamesOf(targets.stream().map(UserVO::getId).toList());
        targets.forEach(a -> a.setBadges(map.get(a.getId())));
    }

    @Override
    public void fillPostAuthors(Collection<PostVO> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        fillAuthors(posts.stream().map(PostVO::getAuthor).toList());
    }

    @Override
    public void fillCommentAuthors(Collection<CommentVO> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        fillAuthors(comments.stream().map(CommentVO::getAuthor).toList());
    }

    @Override
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        stringRedisTemplate.delete(RedisKeyConstants.BADGE_USER + userId);
    }

    /* ==================== 缓存读写 ==================== */

    /** 读缓存；未命中则查库并回填（单用户路径） */
    private List<BadgeVO> loadFromCache(Long userId) {
        String key = RedisKeyConstants.BADGE_USER + userId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            return parse(json);
        }
        List<BadgeVO> badges = activityOrderMapper.selectBadgesByUserId(userId);
        writeCache(userId, badges);
        return badges;
    }

    private void writeCache(Long userId, List<BadgeVO> badges) {
        try {
            stringRedisTemplate.opsForValue().set(RedisKeyConstants.BADGE_USER + userId,
                    objectMapper.writeValueAsString(badges), RedisKeyConstants.BADGE_USER_TTL);
        } catch (JsonProcessingException e) {
            // 序列化失败只影响缓存命中率，不影响业务正确性
            log.warn("徽章缓存序列化失败 userId={}", userId, e);
        }
    }

    /** 缓存反序列化失败时按"无徽章"处理，宁可少显示也不要让整个列表接口 500 */
    private List<BadgeVO> parse(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructParametricType(List.class, BadgeVO.class));
        } catch (JsonProcessingException e) {
            log.warn("徽章缓存反序列化失败，按空处理", e);
            return List.of();
        }
    }

    /** 过滤掉 null 与缺 ID 的作者（列表里可能存在已注销用户） */
    private List<UserVO> nonNullAuthors(Collection<UserVO> authors) {
        if (authors == null || authors.isEmpty()) {
            return List.of();
        }
        return authors.stream()
                .filter(a -> a != null && a.getId() != null)
                .toList();
    }
}
