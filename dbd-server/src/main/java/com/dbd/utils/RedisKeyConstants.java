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
}
