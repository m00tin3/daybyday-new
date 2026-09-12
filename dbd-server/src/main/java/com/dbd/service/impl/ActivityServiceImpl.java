package com.dbd.service.impl;

import com.dbd.common.BusinessException;
import com.dbd.entity.Activity;
import com.dbd.entity.ActivityOrder;
import com.dbd.mapper.ActivityMapper;
import com.dbd.service.ActivityService;
import com.dbd.service.SeckillOrderPersistService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.utils.UserContext;
import com.dbd.vo.ActivityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀服务实现。Redis 技术点（对应 PROJECT_PLAN.md §5.1 模块 10）：
 * <ul>
 *   <li>Lua 原子脚本：库存判断 + 库存预扣 + 一人一单 SETNX，单次 Redis 往返（{@code lua/seckill.lua}）</li>
 *   <li>一人一单双保险：Redis {@code dbd:seckill:order} 标记 + DB 唯一索引 {@code uk_activity_user}</li>
 *   <li>异步落库 + 失败补偿回滚（见 {@link SeckillOrderPersistService}）</li>
 *   <li>抢楼楼层号：stock - remainStock（第几个抢到），限量徽章不返回楼层</li>
 * </ul>
 */
@Slf4j
@Service
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final SeckillOrderPersistService persistService;

    /** 秒杀 Lua 脚本（库存预扣 + 一人一单） */
    private final DefaultRedisScript<Long> seckillScript;

    public ActivityServiceImpl(ActivityMapper activityMapper,
                               StringRedisTemplate stringRedisTemplate,
                               RedisIdWorker redisIdWorker,
                               SeckillOrderPersistService persistService) {
        this.activityMapper = activityMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
        this.persistService = persistService;
        this.seckillScript = new DefaultRedisScript<>();
        this.seckillScript.setLocation(new ClassPathResource("lua/seckill.lua"));
        this.seckillScript.setResultType(Long.class);
    }

    /* ==================== 活动详情 ==================== */

    @Override
    public ActivityVO detail(Long id) {
        Activity activity = requireActivity(id);
        ActivityVO vo = ActivityVO.from(activity);
        // 状态按起止时间动态计算（DB status 作为落库基准，实时以时间为准）
        vo.setStatus(resolveStatus(activity));

        // 剩余库存实时读 Redis（未预热则回退 DB 总量）
        String remain = stringRedisTemplate.opsForValue().get(RedisKeyConstants.SECKILL_STOCK + id);
        vo.setRemainStock(remain == null ? activity.getStock() : Integer.parseInt(remain));

        // 当前用户是否已抢/已领（一人一单标记）
        Long userId = UserContext.get();
        vo.setGrabbed(userId != null && Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(RedisKeyConstants.SECKILL_ORDER + id + ":" + userId)));
        return vo;
    }

    /* ==================== 抢楼 / 领徽章 ==================== */

    @Override
    public Map<String, Object> grab(Long id) {
        Long userId = UserContext.get();
        if (userId == null) {
            throw BusinessException.forbidden("请先登录");
        }
        Activity activity = requireActivity(id);
        int status = resolveStatus(activity);
        if (status != 1) {
            throw BusinessException.seckill(4001, status == 0 ? "活动尚未开始" : "活动已结束");
        }

        // 库存预热：SETNX 仅在无值时初始化（避免覆盖活动进行中的实时库存）
        String stockKey = RedisKeyConstants.SECKILL_STOCK + id;
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(activity.getStock()));
        String orderKey = RedisKeyConstants.SECKILL_ORDER + id + ":" + userId;

        // Lua 原子预扣：0 成功 / 1 库存不足 / 2 重复抢
        Long r = stringRedisTemplate.execute(seckillScript,
                List.of(stockKey, orderKey), String.valueOf(userId));
        if (r == null || r == 1) {
            throw BusinessException.seckill(4002, "手慢了，已被抢光");
        }
        if (r == 2) {
            throw BusinessException.seckill(4003, "你已抢过，不能重复参与");
        }

        // 成功：生成订单（订单号复用全局唯一 ID），异步落库
        ActivityOrder order = new ActivityOrder();
        order.setId(redisIdWorker.nextId("activityOrder"));
        order.setActivityId(id);
        order.setUserId(userId);
        order.setStatus(0);
        order.setOrderNo(String.valueOf(order.getId()));
        persistService.persist(order, id, userId);

        // 抢楼楼层号 = 已抢数量 = stock - remainStock（限量徽章不返回楼层）
        Map<String, Object> result = new HashMap<>();
        // orderId 为 Redis 全局 ID（18-19 位），转字符串避免前端 JS 精度丢失
        result.put("orderId", String.valueOf(order.getId()));
        if (activity.getType() == 1) {
            String remain = stringRedisTemplate.opsForValue().get(stockKey);
            int remainStock = remain == null ? activity.getStock() : Integer.parseInt(remain);
            result.put("floorNo", activity.getStock() - remainStock);
        }
        log.info("秒杀成功 activityId={}, userId={}, orderId={}, remain={}",
                id, userId, order.getId(), stringRedisTemplate.opsForValue().get(stockKey));
        return result;
    }

    /* ==================== 工具 ==================== */

    private Activity requireActivity(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw BusinessException.notFound("活动不存在");
        }
        return activity;
    }

    /** 按当前时间动态计算活动状态：0 未开始 / 1 进行中 / 2 已结束 */
    private int resolveStatus(Activity a) {
        LocalDateTime now = LocalDateTime.now();
        if (a.getBeginTime() != null && now.isBefore(a.getBeginTime())) {
            return 0;
        }
        if (a.getEndTime() != null && now.isAfter(a.getEndTime())) {
            return 2;
        }
        return 1;
    }
}
