package com.dbd.utils;

import java.time.Duration;

/**
 * Redis Key 全局常量（对应 PROJECT_PLAN.md §5.4 Key 规范，前缀统一 dbd:）。
 * 各业务模块复用自己的 Key 与 TTL，避免散落定义。
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    /* ---------- 认证（AuthServiceImpl） ---------- */

    /** 验证码：dbd:verify:code:{phone} → 6 位验证码，5 分钟 */
    public static final String VERIFY_CODE = "dbd:verify:code:";
    public static final Duration VERIFY_CODE_TTL = Duration.ofMinutes(5);

    /** 防重发锁：dbd:verify:lock:{phone} → 1，60 秒（SETNX 原子占用） */
    public static final String VERIFY_CODE_LOCK = "dbd:verify:lock:";
    public static final Duration VERIFY_CODE_LOCK_TTL = Duration.ofSeconds(60);

    /** 登录会话：dbd:login:token:{token} → userId，30 分钟，拦截器滑动续期 */
    public static final String LOGIN_TOKEN = "dbd:login:token:";
    public static final Duration LOGIN_TOKEN_TTL = Duration.ofMinutes(30);

    /* ---------- 帖子（PostServiceImpl） ---------- */

    /** 帖子详情缓存：dbd:post:cache:{postId} → PostVO JSON（空值也缓存防穿透） */
    public static final String POST_CACHE = "dbd:post:cache:";
    /** 首页列表缓存：dbd:post:list:home:{page}（带筛选条件的不缓存） */
    public static final String POST_LIST_HOME = "dbd:post:list:home:";

    /**
     * 城市列表缓存：dbd:post:cities → CityStatVO JSON 数组（有帖子的城市 + 数量）。
     * 该结果由 GROUP BY 聚合得出，访问频繁但变化不频繁，适合短 TTL 缓存。
     */
    public static final String POST_CITIES = "dbd:post:cities";
    public static final Duration POST_CITIES_TTL = Duration.ofMinutes(5);

    /** 帖子点赞 Set：dbd:post:like:{postId} → userId 集合 */
    public static final String POST_LIKE = "dbd:post:like:";
    /** 帖子收藏 Set：dbd:post:favorite:{postId} → userId 集合 */
    public static final String POST_FAVORITE = "dbd:post:favorite:";

    /** 楼层号：dbd:post:floor:{postId} → INCR 递增 */
    public static final String POST_FLOOR = "dbd:post:floor:";

    /** 帖子独立访客 UV：dbd:post:uv:{postId} → HyperLogLog */
    public static final String POST_UV = "dbd:post:uv:";

    /** 帖子浏览量：dbd:post:view:{postId} → INCR 计数 */
    public static final String POST_VIEW = "dbd:post:view:";

    /** 发帖防重复提交：dbd:repeat:post:{userId}，3 秒（SETNX） */
    public static final String REPEAT_POST = "dbd:repeat:post:";
    public static final Duration REPEAT_POST_TTL = Duration.ofSeconds(3);

    /** 回帖防重复提交：dbd:repeat:comment:{userId}，3 秒（SETNX） */
    public static final String REPEAT_COMMENT = "dbd:repeat:comment:";
    public static final Duration REPEAT_COMMENT_TTL = Duration.ofSeconds(3);

    /* ---------- 吧（BarServiceImpl） ---------- */

    /** 吧信息缓存：dbd:bar:cache:{barId} → BarVO JSON（空值也缓存） */
    public static final String BAR_CACHE = "dbd:bar:cache:";

    /** 吧关注人数计数：dbd:bar:member:{barId} → INCR/DECR */
    public static final String BAR_MEMBER = "dbd:bar:member:";

    /** 签到 BitMap：dbd:sign:{userId}:{yyyyMM} → 每位代表一天 */
    public static final String SIGN = "dbd:sign:";

    /** 热吧榜：dbd:rank:hot:bar → ZSet，score=memberCount，定时重算 */
    public static final String RANK_HOT_BAR = "dbd:rank:hot:bar";

    /* ---------- 用户/排行/搜索（阶段二） ---------- */

    /** 用户粉丝数：dbd:user:fan:{userId} → INCR/DECR */
    public static final String USER_FAN = "dbd:user:fan:";

    /**
     * 用户角色缓存：dbd:user:role:{userId} → 0普通 / 1管理员。
     * 管理接口鉴权用，避免每个管理请求都查库；角色极少变动，10 分钟 TTL。
     */
    public static final String USER_ROLE = "dbd:user:role:";
    public static final Duration USER_ROLE_TTL = Duration.ofMinutes(10);

    /** 热帖榜：dbd:rank:hot:post → ZSet，score=热度分（view + like*2 + comment*4），定时重算 */
    public static final String RANK_HOT_POST = "dbd:rank:hot:post";

    /** 热搜词：dbd:search:hot → ZSet，score=搜索次数 */
    public static final String SEARCH_HOT = "dbd:search:hot";

    /* ---------- 秒杀（阶段三，ActivityServiceImpl） ---------- */

    /** 秒杀库存预扣：dbd:seckill:stock:{activityId} → 剩余库存（活动开始前从 DB 初始化） */
    public static final String SECKILL_STOCK = "dbd:seckill:stock:";
    /** 一人一单标记：dbd:seckill:order:{activityId}:{userId} → userId（Lua SETNX 原子写入，永久） */
    public static final String SECKILL_ORDER = "dbd:seckill:order:";

    /* ---------- 关注 Feed 流（阶段三，FeedServiceImpl） ---------- */

    /** 关注 Feed 时间线：dbd:feed:user:{userId} → ZSet，member=postId，score=发帖时间戳(ms) */
    public static final String FEED_USER = "dbd:feed:user:";

    /* ---------- 同城 GEO（阶段三，NearbyServiceImpl） ---------- */

    /** 附近帖子 GEO：dbd:geo:post → GEO 集合，member=postId，坐标=发帖经纬度 */
    public static final String GEO_POST = "dbd:geo:post";
}
