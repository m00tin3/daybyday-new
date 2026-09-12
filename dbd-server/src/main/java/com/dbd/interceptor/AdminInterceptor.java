package com.dbd.interceptor;

import com.dbd.service.UserService;
import com.dbd.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 管理员权限拦截器，仅作用于 {@code /api/admin/**}。
 *
 * <p>执行顺序：必须排在 {@link LoginInterceptor} 之后（order 更大），
 * 由后者先解析 token 并写入 {@link UserContext}。</p>
 *
 * <p>与登录拦截器的差异：登录拦截器对 GET 是"可选登录"（匿名放行，论坛读公开），
 * 而管理接口即便是 GET 也必须登录且为管理员，因此这里对未登录补一次 401 判定。</p>
 *
 * <p>鉴权失败直接写 JSON 响应（与 {@code Result} 结构一致），
 * 避免抛异常后绕过拦截器链导致返回体不统一。</p>
 */
public class AdminInterceptor implements HandlerInterceptor {

    /** 无权限错误码，与 BusinessException.forbidden 保持一致（API.md §1.5 / 2003） */
    private static final int CODE_FORBIDDEN = 2003;

    private final UserService userService;

    public AdminInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Long userId = UserContext.get();
        if (userId == null) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "-1", "未登录");
            return false;
        }
        if (!userService.isAdmin(userId)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, String.valueOf(CODE_FORBIDDEN), "需要管理员权限");
            return false;
        }
        return true;
    }

    /** 直接输出统一结构 JSON，避免走异常处理器造成响应体不一致 */
    private void writeJson(HttpServletResponse response, int status, String code, String msg) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
