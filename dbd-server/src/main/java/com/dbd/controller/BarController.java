package com.dbd.controller;

import com.dbd.common.PageResult;
import com.dbd.common.Result;
import com.dbd.service.BarService;
import com.dbd.vo.BarVO;
import com.dbd.vo.PostVO;
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
 * 吧接口（对应 API.md §3.3）。
 */
@Tag(name = "吧模块")
@RestController
@RequestMapping("/api/bar")
public class BarController {

    private final BarService barService;

    public BarController(BarService barService) {
        this.barService = barService;
    }

    @Operation(summary = "吧信息")
    @GetMapping("/{id}")
    public Result<BarVO> info(@PathVariable Long id) {
        return Result.ok(barService.info(id));
    }

    @Operation(summary = "吧内帖子（分页）")
    @GetMapping("/{id}/posts")
    public Result<PageResult<PostVO>> posts(@PathVariable Long id,
                                            @RequestParam(required = false, defaultValue = "1") Integer page,
                                            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(barService.posts(id, page, size));
    }

    @Operation(summary = "吧签到（BitMap）🔒")
    @PostMapping("/{id}/sign")
    public Result<Map<String, Object>> sign(@PathVariable Long id) {
        return Result.ok(barService.sign(id), "签到成功");
    }

    @Operation(summary = "关注/取消关注吧（幂等切换）🔒")
    @PostMapping("/{id}/follow")
    public Result<Map<String, Object>> follow(@PathVariable Long id) {
        return Result.ok(barService.follow(id));
    }

    @Operation(summary = "热吧榜（ZSet，最多 10）")
    @GetMapping("/rank")
    public Result<List<BarVO>> rank() {
        return Result.ok(barService.rank());
    }
}
