package com.dbd.utils;

/**
 * 当前登录用户上下文：ThreadLocal 保存，请求结束后由拦截器清理。
 * <p>由 {@link com.dbd.interceptor.LoginInterceptor} 写入，业务层通过 {@link #get()} 获取当前用户 ID。</p>
 */
public class UserContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    /** 写入当前请求的用户 ID */
    public static void set(Long userId) {
        HOLDER.set(userId);
    }

    /** 获取当前用户 ID（未登录/骨架阶段为 null） */
    public static Long get() {
        return HOLDER.get();
    }

    /** 清理（afterCompletion 调用，防止线程池复用导致串号） */
    public static void remove() {
        HOLDER.remove();
    }
}
