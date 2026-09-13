package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbd.common.BusinessException;
import com.dbd.common.PageResult;
import com.dbd.entity.Activity;
import com.dbd.entity.ActivityOrder;
import com.dbd.mapper.ActivityMapper;
import com.dbd.service.ActivityService;
import com.dbd.service.BadgeService;
import com.dbd.service.SeckillOrderPersistService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.utils.UserContext;
import com.dbd.vo.ActivityVO;
import com.dbd.vo.BadgeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 秒杀服务实现。Redis 技术点（对应 PROJECT_PLAN.md §5.1 模块 10）：
 * <ul>
 *   <li>Lua 原子脚本：库存判断 + 库存预扣 + 一人一单 SETNX，单次 Redis 往返（{@code lua/seckill.lua}）</li>
 *   <li>一人一单双保险：Redis {@code dbd:seckill:order} 标记 + DB 唯一索引 {@code uk_activity_user}</li>
 *   <li>异步落库 + 失败补偿回滚（见 {@link SeckillOrderPersistService}）</li>
 *   <li>抢楼楼层号：stock - remainStock（第几个抢到），限量徽章不返回楼层</li>
 *   <li>列表页库存/已抢状态：**一次 MGET** 批量取，避免循环单键 GET</li>
 * </ul>
 */
@Slf4j
@Service
public class ActivityServiceImpl implements ActivityService {

    /** 管理端只发布这一种；抢楼为历史类型，保留兼容 */
    private static final int TYPE_FLOOR = 1;

    /** 活动类型：限量徽章 */
    private static final int TYPE_BADGE = 2;

    /** 活动状态：已结束（管理员显式结束或自然到期） */
    private static final int STATUS_ENDED = 2;

    private final ActivityMapper activityMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final SeckillOrderPersistService persistService;
    private final BadgeService badgeService;

    /** 秒杀 Lua 脚本（库存预扣 + 一人一单） */
    private final DefaultRedisScript<Long> seckillScript;

    public ActivityServiceImpl(ActivityMapper activityMapper,
                               StringRedisTemplate stringRedisTemplate,
                               RedisIdWorker redisIdWorker,
                               SeckillOrderPersistService persistService,
                               BadgeService badgeService) {
        this.activityMapper = activityMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
        this.persistService = persistService;
        this.badgeService = badgeService;
        this.seckillScript = new DefaultRedisScript<>();
        this.seckillScript.setLocation(new ClassPathResource("lua/seckill.lua"));
        this.seckillScript.setResultType(Long.class);
    }

    /* ==================== 活动列表 ==================== */

    @Override
    public PageResult<ActivityVO> list(Integer type, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 12 : Math.min(size, 50);
        IPage<Activity> result = activityMapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<Activity>()
                        .eq(type != null, Activity::getType, type)
                        .orderByDesc(Activity::getBeginTime)
                        .orderByDesc(Activity::getId));
        return PageResult.of(toVOList(result.getRecords()), result.getTotal(), p, s);
    }

    /* ==================== 活动详情 ==================== */

    @Override
    public ActivityVO detail(Long id) {
        Activity activity = requireActivity(id);
        return toVOList(List.of(activity)).get(0);
    }

    @Override
    public List<ActivityVO> toVOList(List<Activity> activities) {
        if (activities == null || activities.isEmpty()) {
            return List.of();
        }

        // 一次 MGET 取全部剩余库存：未预热的 key 返回 null，回退 DB 总量（即"一个没抢"）
        List<String> stockKeys = activities.stream()
                .map(a -> RedisKeyConstants.SECKILL_STOCK + a.getId())
                .toList();
        List<String> stocks = stringRedisTemplate.opsForValue().multiGet(stockKeys);

        Long userId = UserContext.get();
        Set<Long> grabbedIds = userId == null ? Set.of() : grabbedActivityIds(activities, userId);

        List<ActivityVO> result = new ArrayList<>(activities.size());
        for (int i = 0; i < activities.size(); i++) {
            Activity a = activities.get(i);
            ActivityVO vo = ActivityVO.from(a);
            // 状态按起止时间动态计算（DB status 作为落库基准，实时以时间为准）
            vo.setStatus(resolveStatus(a));

            String remain = stocks == null || i >= stocks.size() ? null : stocks.get(i);
            int remainStock = remain == null ? a.getStock() : Integer.parseInt(remain);
            vo.setRemainStock(remainStock);
            vo.setAwardedCount(Math.max(0, a.getStock() - remainStock));
            vo.setGrabbed(grabbedIds.contains(a.getId()));
            result.add(vo);
        }
        return result;
    }

    /** 批量判断当前用户已抢哪些活动：同样一次 MGET，而不是逐个 hasKey */
    private Set<Long> grabbedActivityIds(List<Activity> activities, Long userId) {
        List<String> keys = activities.stream()
                .map(a -> RedisKeyConstants.SECKILL_ORDER + a.getId() + ":" + userId)
                .toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        Set<Long> grabbed = new HashSet<>();
        if (values == null) {
            return grabbed;
        }
        for (int i = 0; i < activities.size() && i < values.size(); i++) {
            if (values.get(i) != null) {
                grabbed.add(activities.get(i).getId());
            }
        }
        return grabbed;
    }

    /* ==================== 徽章墙 ==================== */

    @Override
    public List<BadgeVO> myBadges() {
        Long userId = UserContext.get();
        if (userId == null) {
            throw BusinessException.forbidden("请先登录");
        }
        return badgeService.userBadges(userId);
    }

    @Override
    public List<BadgeVO> userBadges(Long userId) {
        return badgeService.userBadges(userId);
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
        if (activity.getType() != null && activity.getType() == TYPE_BADGE) {
            result.put("badgeName", activity.getBadgeName());
            result.put("message", "恭喜获得限量徽章「" + activity.getBadgeName() + "」");
        } else {
            String remain = stringRedisTemplate.opsForValue().get(stockKey);
            int remainStock = remain == null ? activity.getStock() : Integer.parseInt(remain);
            int floorNo = activity.getStock() - remainStock;
            result.put("floorNo", floorNo);
            result.put("message", "恭喜！你是第 " + floorNo + " 楼");
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

    /**
     * 按当前时间动态计算活动状态：0 未开始 / 1 进行中 / 2 已结束。
     *
     * <p>先看 DB 的显式结束标记：管理员「提前结束」会把 status 写成 2，此时必须立即生效。
     * 若只按时间判断，由于 end_time 列是秒精度的 DATETIME，写入时小数秒会被 MySQL
     * 四舍五入（如 15.7 秒 → 16 秒），导致结束后最多 1 秒内仍被判为「进行中」。</p>
     */
    private int resolveStatus(Activity a) {
        if (a.getStatus() != null && a.getStatus() == STATUS_ENDED) {
            return STATUS_ENDED;
        }
        LocalDateTime now = LocalDateTime.now();
        if (a.getBeginTime() != null && now.isBefore(a.getBeginTime())) {
            return 0;
        }
        if (a.getEndTime() != null && now.isAfter(a.getEndTime())) {
            return STATUS_ENDED;
        }
        return 1;
    }
}
