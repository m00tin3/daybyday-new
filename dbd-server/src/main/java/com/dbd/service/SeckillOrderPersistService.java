package com.dbd.service;

import com.dbd.entity.ActivityOrder;
import com.dbd.mapper.ActivityOrderMapper;
import com.dbd.utils.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 秒杀订单异步落库服务。
 * <p>Lua 预扣成功后主流程立即返回，订单写 {@code activity_order} 表放到独立线程异步完成；
 * 若落库失败（如 DB 抖动 / 唯一索引兜底冲突），在本线程补偿回滚：库存 +1 + 删除一人一单标记，
 * 保证 Redis 与 DB 最终一致（对应 API.md §3.6.2 要点 3）。</p>
 */
@Slf4j
@Service
public class SeckillOrderPersistService {

    private final ActivityOrderMapper activityOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final BadgeService badgeService;
    /**
     * 首页列表缓存里存的是整份 PostVO（含作者徽章），发徽章后必须一并失效，
     * 否则抢完回首页，昵称旁的角标要等列表 TTL（60 秒）才出现。
     */
    private final PostService postService;

    public SeckillOrderPersistService(ActivityOrderMapper activityOrderMapper,
                                      StringRedisTemplate stringRedisTemplate,
                                      BadgeService badgeService,
                                      PostService postService) {
        this.activityOrderMapper = activityOrderMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.badgeService = badgeService;
        this.postService = postService;
    }

    /**
     * 异步写订单；失败则回滚库存并释放一人一单标记。
     */
    @Async
    public void persist(ActivityOrder order, Long activityId, Long userId) {
        try {
            activityOrderMapper.insert(order);
            // 必须在 insert 成功之后再失效缓存：提前删的话，紧接着的读请求
            // 会把"还没有徽章"的空结果重新缓存进去，用户要等 TTL 过期才看得到
            badgeService.evict(userId);
            postService.evictHomeListCache();
            log.info("秒杀订单落库成功 orderId={}, activityId={}, userId={}",
                    order.getId(), activityId, userId);
        } catch (Exception e) {
            log.error("秒杀订单落库失败，补偿回滚 orderId={}, activityId={}, userId={}",
                    order.getId(), activityId, userId, e);
            // 补偿：库存回补 + 删除一人一单标记，允许用户重新抢
            stringRedisTemplate.opsForValue().increment(RedisKeyConstants.SECKILL_STOCK + activityId);
            stringRedisTemplate.delete(RedisKeyConstants.SECKILL_ORDER + activityId + ":" + userId);
        }
    }
}
