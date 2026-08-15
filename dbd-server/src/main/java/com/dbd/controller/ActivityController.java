package com.dbd.controller;

import com.dbd.common.Result;
import com.dbd.service.ActivityService;
import com.dbd.vo.ActivityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 秒杀活动接口（对应 API.md §3.6，阶段三核心）。
 */
@Tag(name = "秒杀模块")
@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Operation(summary = "活动详情（剩余库存实时读 Redis）")
    @GetMapping("/{id}")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }

    @Operation(summary = "抢楼/领取徽章（Lua 原子预扣 + 一人一单）🔒")
    @PostMapping("/{id}/grab")
    public Result<Map<String, Object>> grab(@PathVariable Long id) {
        return Result.ok(activityService.grab(id), "抢楼成功");
    }
}
