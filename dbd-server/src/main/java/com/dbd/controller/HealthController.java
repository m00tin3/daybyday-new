package com.dbd.controller;

import com.dbd.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口：验证服务与 Redis 连通性，联调排障用。
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/api")
public class HealthController {

    private final StringRedisTemplate stringRedisTemplate;

    public HealthController(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Operation(summary = "健康检查（验证服务与 Redis 连通）")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("status", "UP");
        info.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        info.put("redis", pingRedis());
        return Result.ok(info);
    }

    /** 探测 Redis 连通性，异常时返回 DOWN + 原因 */
    private String pingRedis() {
        try {
            return "PONG".equals(stringRedisTemplate.getConnectionFactory().getConnection().ping()) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }
}
