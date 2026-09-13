package com.dbd.controller;

import com.dbd.common.PageResult;
import com.dbd.common.Result;
import com.dbd.service.ActivityService;
import com.dbd.vo.ActivityVO;
import com.dbd.vo.BadgeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    @Operation(summary = "活动列表（活动广场，type=2 为限量徽章）")
    @GetMapping
    public Result<PageResult<ActivityVO>> list(@RequestParam(required = false) Integer type,
                                               @RequestParam(required = false, defaultValue = "1") Integer page,
                                               @RequestParam(required = false, defaultValue = "12") Integer size) {
        return Result.ok(activityService.list(type, page, size));
    }

    @Operation(summary = "我的徽章墙 🔒需登录")
    @GetMapping("/my/badges")
    public Result<List<BadgeVO>> myBadges() {
        return Result.ok(activityService.myBadges());
    }

    @Operation(summary = "指定用户的徽章墙（他人主页，公开）")
    @GetMapping("/user/{userId}/badges")
    public Result<List<BadgeVO>> userBadges(@PathVariable Long userId) {
        return Result.ok(activityService.userBadges(userId));
    }

    @Operation(summary = "活动详情（剩余库存实时读 Redis）")
    @GetMapping("/{id}")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }

    @Operation(summary = "抢楼/领取徽章（Lua 原子预扣 + 一人一单）🔒")
    @PostMapping("/{id}/grab")
    public Result<Map<String, Object>> grab(@PathVariable Long id) {
        // 提示语由服务层按活动类型给出（抢楼报楼层、徽章报称号），见 data.message
        return Result.ok(activityService.grab(id), "领取成功");
    }
}
