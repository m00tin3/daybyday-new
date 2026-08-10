package com.dbd.interceptor;

import com.dbd.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 登录态拦截器。
 * <p>规则：</p>
 * <ul>
 *   <li><b>写操作</b>（POST 等）：必须携带有效 token（{@code Authorization: Bearer <token>} →
 *       Redis {@code dbd:login:token:<token>} 查 userId），未命中返回 HTTP 401</li>
 *   <li><b>GET 读请求</b>：可选登录——带有效 token 则填充 {@link UserContext}（请求级状态如
 *       isLiked/signedToday 才能生效），无/无效 token 则以匿名身份放行（论坛"读公开"）</li>
 * </ul>
 * <p>token 命中即续期（滑动过期 30 分钟）。</p>
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
        String method = request.getMethod();
        if ("GET".equals(method) || "OPTIONS".equals(method)) {
            resolveOptionalLogin(request);
            return true;
        }
        return requireLogin(request, response);
    }

    /** 写操作：必须登录，否则 401 */
    private boolean requireLogin(HttpServletRequest request, HttpServletResponse response) {
        String userId = resolveUserId(request);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        UserContext.set(Long.valueOf(userId));
        return true;
    }

    /** GET 读请求：可选登录（匿名也放行） */
    private void resolveOptionalLogin(HttpServletRequest request) {
        String userId = resolveUserId(request);
        if (userId != null) {
            UserContext.set(Long.valueOf(userId));
        }
    }

    /** 解析并校验 token：有效返回 userId（并滑动续期），否则返回 null */
    private String resolveUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        String token = auth.substring(7);
        String userId = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (userId == null) {
            return null;
        }
        stringRedisTemplate.expire(TOKEN_PREFIX + token, TOKEN_TTL);
        return userId;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.remove();
    }
}
