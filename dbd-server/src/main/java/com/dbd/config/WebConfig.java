package com.dbd.config;

import com.dbd.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录拦截器。
 * <p>公开路径（无需 token）：
 * {@code /api/auth/code}（发送验证码）、{@code /api/auth/login}、{@code /api/auth/register}、{@code /api/health}。
 * 其余 /api/** 均需 Bearer token，见 API.md §1.3。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;

    public WebConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/code",
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/health"
                );
    }
}
