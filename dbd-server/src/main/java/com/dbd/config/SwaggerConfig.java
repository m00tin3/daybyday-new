package com.dbd.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置（SpringDoc）。
 * <p>访问入口：{@code /swagger-ui.html}；接口文档与 API.md 保持一致，接口签名以此为准。</p>
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI dbdOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Day-BY-Day 接口文档")
                .version("0.1.0")
                .description("统一响应 {code, msg, data}；code=1 成功；鉴权头 Authorization: Bearer <token>"));
    }
}
