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
}
