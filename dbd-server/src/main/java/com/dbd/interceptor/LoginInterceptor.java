package com.dbd.interceptor;

import com.dbd.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 登录态拦截器（🔒 写操作）。
 * <p>规则：GET 读请求公开放行（论坛读公开）；POST 等写操作取 {@code Authorization: Bearer <token>}
 * → 查 Redis {@code dbd:login:token:<token>} → 命中则写入 {@link UserContext} 并续期（滑动过期 30 分钟），
 * 未命中返回 HTTP 401。token 与用户 ID 的绑定关系在登录接口写入（见 AuthServiceImpl）。</p>
 */
public class LoginInterceptor implements HandlerInterceptor {

    /** Redis Key 前缀：dbd:login:token:{token} */
    private static final String TOKEN_PREFIX = "dbd:login:token:";

    /** token 有效期：30 分钟，每次请求滑动续期 */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // GET 读请求公开（列表/详情/楼层等）；写操作需登录
        if ("GET".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        String token = auth.substring(7);
        String userId = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        UserContext.set(Long.valueOf(userId));
        // 滑动续期：只要 30 分钟内有请求，token 不过期
        stringRedisTemplate.expire(TOKEN_PREFIX + token, TOKEN_TTL);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.remove();
    }
}
