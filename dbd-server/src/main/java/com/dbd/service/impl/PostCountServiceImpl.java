package com.dbd.service.impl;

import com.dbd.service.PostCountService;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.vo.PostVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 帖子计数回填实现，说明见 {@link PostCountService}。
 */
@Slf4j
@Service
public class PostCountServiceImpl implements PostCountService {

    private final StringRedisTemplate stringRedisTemplate;

    public PostCountServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void fill(List<PostVO> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        int n = posts.size();

        // SCARD 没有 MGET 那样的批量形式，用一次 pipeline 把 2N 条命令一起发出去，
        // 避免逐帖两次往返（列表一页最多 50 条，逐个就是 100 次 RTT）
        List<Object> sizes = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (PostVO vo : posts) {
                connection.setCommands().sCard(raw(RedisKeyConstants.POST_LIKE + vo.getId()));
            }
            for (PostVO vo : posts) {
                connection.setCommands().sCard(raw(RedisKeyConstants.POST_FAVORITE + vo.getId()));
            }
            return null;
        });

        if (sizes == null || sizes.size() < n * 2) {
            log.warn("计数回填的 pipeline 返回条数不符：期望 {}，实际 {}", n * 2,
                    sizes == null ? "null" : sizes.size());
            return;
        }
        for (int i = 0; i < n; i++) {
            posts.get(i).setLikeCount(asLong(sizes.get(i)));
            posts.get(i).setFavoriteCount(asLong(sizes.get(n + i)));
        }
    }

    private static byte[] raw(String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    /** SCARD 的返回是整数应答；key 不存在时 Redis 返回 0，不会是 nil */
    private static long asLong(Object reply) {
        return reply instanceof Number num ? num.longValue() : 0L;
    }
}
