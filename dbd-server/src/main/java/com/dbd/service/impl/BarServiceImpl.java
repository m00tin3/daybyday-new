package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dbd.common.BusinessException;
import com.dbd.common.PageResult;
import com.dbd.entity.Bar;
import com.dbd.entity.Follow;
import com.dbd.mapper.BarMapper;
import com.dbd.mapper.FollowMapper;
import com.dbd.service.BarService;
import com.dbd.service.PostService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.utils.UserContext;
import com.dbd.vo.BarVO;
import com.dbd.vo.PostVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 吧服务实现。Redis 技术点（对应 PROJECT_PLAN.md §5.1 模块 5/6）：
 * <ul>
 *   <li>吧信息缓存 + 随机 TTL（防雪崩），memberCount 实时取 Redis 计数并覆盖</li>
 *   <li>签到：BitMap {@code dbd:sign:{userId}:{yyyyMM}}，GETBIT 判重 + SETBIT 写入 + BITFIELD 查连续天数 + BITCOUNT 累计</li>
 *   <li>关注：follow 表（type=2）+ Redis 计数 INCR/DECR</li>
 *   <li>热吧榜：ZSet（score=memberCount），@Scheduled 每 5 分钟重算 + 关注时实时 ZADD</li>
 * </ul>
 */
@Slf4j
@Service
public class BarServiceImpl implements BarService {

    private final BarMapper barMapper;
    private final FollowMapper followMapper;
    private final PostService postService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 吧信息缓存 TTL：5 分钟 + 0~3 分钟随机（防雪崩） */
    private static final Duration BAR_CACHE_TTL = Duration.ofMinutes(5);
    private static final long BAR_CACHE_JITTER = 3 * 60;

    public BarServiceImpl(BarMapper barMapper, FollowMapper followMapper, PostService postService,
                          StringRedisTemplate stringRedisTemplate, RedisIdWorker redisIdWorker) {
        this.barMapper = barMapper;
        this.followMapper = followMapper;
        this.postService = postService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
    }

    /* ==================== 吧信息 ==================== */

    @Override
    public BarVO info(Long id) {
        String key = RedisKeyConstants.BAR_CACHE + id;
        String cached = stringRedisTemplate.opsForValue().get(key);
        BarVO vo;
        if (cached != null && !cached.isEmpty()) {
            vo = parse(cached, id);
        } else {
            Bar bar = barMapper.selectById(id);
            if (bar == null || bar.getStatus() == 0) {
                throw BusinessException.notFound("吧不存在");
            }
            vo = BarVO.from(bar);
            cache(key, vo);
        }
        fillRequestState(id, vo);
        return vo;
    }

    /** 请求级状态：memberCount 取 Redis 实时计数覆盖；isFollowed/signedToday 实时查 */
    private void fillRequestState(Long id, BarVO vo) {
        ensureMemberCount(id);
        String memberCount = stringRedisTemplate.opsForValue().get(RedisKeyConstants.BAR_MEMBER + id);
        if (memberCount != null) {
            vo.setMemberCount(Long.valueOf(memberCount));
        }
        Long userId = UserContext.get();
        if (userId == null) {
            vo.setIsFollowed(false);
            vo.setSignedToday(false);
            return;
        }
        vo.setIsFollowed(isFollowed(userId, id));
        vo.setSignedToday(isSignedToday(userId));
    }

    @Override
    public PageResult<PostVO> posts(Long id, Integer page, Integer size) {
        requireBar(id);
        return postService.page(id, null, null, page, size);
    }

    /* ==================== 签到（BitMap） ==================== */

    @Override
    public Map<String, Object> sign(Long id) {
        requireBar(id);
        Long userId = UserContext.get();
        LocalDate today = LocalDate.now();
        String key = RedisKeyConstants.SIGN + userId + ":" + today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int offset = today.getDayOfMonth() - 1;

        // 今日已签 → 4004（错误码表）
        Boolean signed = stringRedisTemplate.opsForValue().getBit(key, offset);
        if (Boolean.TRUE.equals(signed)) {
            throw BusinessException.seckill(4004, "今日已签到，明天再来吧");
        }
        // 写入今日位
        stringRedisTemplate.opsForValue().setBit(key, offset, true);

        // BITFIELD 取当月 0~(offset) 位，从今天开始向前数连续 1 的个数（连续签到天数）
        long continuous = continuousDays(key, offset);
        // BITCOUNT 统计本月总签到天数
        Long total = stringRedisTemplate.execute((RedisCallback<Long>) conn -> conn.bitCount(key.getBytes()));

        Map<String, Object> result = new HashMap<>();
        result.put("signedDays", continuous);
        result.put("signCount", total);
        result.put("award", continuous >= 7 ? "连续签到 7 天达成，获得「坚持不懈」徽章" : "");
        return result;
    }

    /** 今日是否已签到 */
    private boolean isSignedToday(Long userId) {
        String key = RedisKeyConstants.SIGN + userId + ":"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        Boolean bit = stringRedisTemplate.opsForValue().getBit(key, LocalDate.now().getDayOfMonth() - 1);
        return Boolean.TRUE.equals(bit);
    }

    /** BITFIELD GET u(offset+1) 0 → 从低位（今天）数连续 1 */
    private long continuousDays(String key, int offset) {
        List<Long> bits = stringRedisTemplate.execute((RedisCallback<List<Long>>) conn -> conn.bitField(key.getBytes(),
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(offset + 1))
                        .valueAt(0)));
        if (bits == null || bits.isEmpty() || bits.get(0) == null) {
            return 1;
        }
        long num = bits.get(0);
        long days = 0;
        while ((num & 1L) == 1L) {
            days++;
            num >>>= 1;
        }
        return days;
    }

    /* ==================== 关注吧 ==================== */

    @Override
    public Map<String, Object> follow(Long id) {
        requireBar(id);
        Long userId = UserContext.get();
        String memberKey = RedisKeyConstants.BAR_MEMBER + id;
        ensureMemberCount(id);

        Follow existing = followMapper.selectOne(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowType, 2)
                .eq(Follow::getFollowBarId, id));
        boolean followed;
        if (existing != null) {
            // 取消关注：删关系 + 计数 -1
            followMapper.deleteById(existing.getId());
            stringRedisTemplate.opsForValue().decrement(memberKey);
            followed = false;
        } else {
            // 关注：建关系（唯一索引 uk_follow 防重）+ 计数 +1
            Follow follow = new Follow();
            follow.setId(redisIdWorker.nextId("follow"));
            follow.setUserId(userId);
            follow.setFollowBarId(id);
            follow.setFollowType(2);
            followMapper.insert(follow);
            stringRedisTemplate.opsForValue().increment(memberKey);
            followed = true;
        }
        // 实时更新热吧榜 score
        Long memberCount = stringRedisTemplate.opsForValue().get(memberKey) == null
                ? 0 : Long.valueOf(stringRedisTemplate.opsForValue().get(memberKey));
        stringRedisTemplate.opsForZSet().add(RedisKeyConstants.RANK_HOT_BAR, String.valueOf(id), memberCount);

        Map<String, Object> result = new HashMap<>();
        result.put("isFollowed", followed);
        result.put("memberCount", memberCount);
        return result;
    }

    private boolean isFollowed(Long userId, Long barId) {
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowType, 2)
                .eq(Follow::getFollowBarId, barId)) > 0;
    }

    /* ==================== 热吧榜（ZSet） ==================== */

    @Override
    public List<BarVO> rank() {
        Set<String> ids = stringRedisTemplate.opsForZSet()
                .reverseRange(RedisKeyConstants.RANK_HOT_BAR, 0, 9);
        if (ids == null || ids.isEmpty()) {
            // 懒构建：ZSet 为空（首次启动）时从 DB 全量重建，演示无需等待定时任务
            rebuildRank();
            ids = stringRedisTemplate.opsForZSet().reverseRange(RedisKeyConstants.RANK_HOT_BAR, 0, 9);
        }
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<BarVO> result = new ArrayList<>();
        for (String id : ids) {
            Bar bar = barMapper.selectById(Long.valueOf(id));
            if (bar != null) {
                result.add(BarVO.from(bar));
            }
        }
        return result;
    }

    /** 定时重算热吧榜：每 5 分钟从 DB 全量重建（score=member_count） */
    @Scheduled(cron = "0 */5 * * * ?")
    @Override
    public void rebuildRank() {
        List<Bar> bars = barMapper.selectList(new LambdaQueryWrapper<Bar>()
                .eq(Bar::getStatus, 1).orderByDesc(Bar::getMemberCount));
        for (Bar bar : bars) {
            stringRedisTemplate.opsForZSet()
                    .add(RedisKeyConstants.RANK_HOT_BAR, String.valueOf(bar.getId()), bar.getMemberCount());
        }
        log.info("热吧榜已重算，共 {} 个吧", bars.size());
    }

    /* ==================== 工具 ==================== */

    private void requireBar(Long id) {
        Bar bar = barMapper.selectById(id);
        if (bar == null || bar.getStatus() == 0) {
            throw BusinessException.notFound("吧不存在");
        }
    }

    /** 关注数计数初始化：Redis 无值则从 DB 种子值同步（member_count 以 Redis 为准，首次使用对齐 DB） */
    private void ensureMemberCount(Long id) {
        String memberKey = RedisKeyConstants.BAR_MEMBER + id;
        if (stringRedisTemplate.opsForValue().get(memberKey) == null) {
            Bar bar = barMapper.selectById(id);
            stringRedisTemplate.opsForValue()
                    .set(memberKey, String.valueOf(bar == null || bar.getMemberCount() == null ? 0 : bar.getMemberCount()));
        }
    }

    private void cache(String key, BarVO vo) {
        long ttl = BAR_CACHE_TTL.toSeconds() + ThreadLocalRandom.current().nextLong(BAR_CACHE_JITTER);
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(vo), Duration.ofSeconds(ttl));
        } catch (JsonProcessingException e) {
            log.warn("吧缓存序列化失败", e);
        }
    }

    private BarVO parse(String json, Long id) {
        try {
            return objectMapper.readValue(json, BarVO.class);
        } catch (JsonProcessingException e) {
            log.warn("吧缓存反序列化失败 barId={}", id, e);
            stringRedisTemplate.delete(RedisKeyConstants.BAR_CACHE + id);
            Bar bar = barMapper.selectById(id);
            return bar == null ? null : BarVO.from(bar);
        }
    }
}
