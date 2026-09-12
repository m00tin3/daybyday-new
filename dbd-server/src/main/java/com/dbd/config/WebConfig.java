package com.dbd.config;

import com.dbd.interceptor.AdminInterceptor;
import com.dbd.interceptor.LoginInterceptor;
import com.dbd.service.UserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录拦截器与管理员拦截器。
 * <p>公开路径（无需 token）：
 * {@code /api/auth/code}（发送验证码）、{@code /api/auth/login}、{@code /api/auth/register}、{@code /api/health}。
 * 其余 /api/** 均需 Bearer token，见 API.md §1.3。</p>
 * <p>{@code /api/admin/**} 额外要求 role=1，见 {@link AdminInterceptor}。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserService userService;

    public WebConfig(StringRedisTemplate stringRedisTemplate, UserService userService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // order(1)：先解析登录态并写入 UserContext
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/code",
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/health"
                )
                .order(1);

        // order(2)：再校验管理员角色（依赖上一步写入 UserContext，顺序不可颠倒）
        registry.addInterceptor(new AdminInterceptor(userService))
                .addPathPatterns("/api/admin/**")
                .order(2);
    }
}
