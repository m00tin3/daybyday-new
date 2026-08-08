package com.dbd.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一 ID 生成器（Redis 自增 + 时间戳）。
 * <p>结构：{@code 41位时间戳(秒) | 32位序列号}，拼成 64 位 long。
 * 序列号按"天"粒度自增（Key：{@code dbd:id:{业务前缀}:{yyyy:MM:dd}}），
 * 单天单业务可支撑 42 亿 ID；相比雪花算法不依赖时钟，相比数据库自增不依赖数据库。</p>
 * <p>用途：帖子 ID、楼层 ID、订单 ID 等（对应 PROJECT_PLAN.md §5.1 模块 3）。</p>
 */
@Component
public class RedisIdWorker {

    /** 起始时间戳：2022-01-01 00:00:00（决定可用 69 年） */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /** 序列号位数：32 位 */
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成全局唯一 ID。
     *
     * @param keyPrefix 业务前缀，如 post / comment / activityOrder
     */
    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("dbd:id:" + keyPrefix + ":" + date);
        return timestamp << COUNT_BITS | count;
    }
}
