package com.dbd.controller;

import com.dbd.common.PageResult;
import com.dbd.common.Result;
import com.dbd.service.UserService;
import com.dbd.vo.PostVO;
import com.dbd.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户接口（对应 API.md §3.4）。
 */
@Tag(name = "用户模块")
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户主页信息")
    @GetMapping("/{id}")
    public Result<UserProfileVO> profile(@PathVariable Long id) {
        return Result.ok(userService.profile(id));
    }

    @Operation(summary = "用户帖子（分页）")
    @GetMapping("/{id}/posts")
    public Result<PageResult<PostVO>> posts(@PathVariable Long id,
                                            @RequestParam(required = false, defaultValue = "1") Integer page,
                                            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(userService.posts(id, page, size));
    }

    @Operation(summary = "我的收藏 🔒（仅本人）")
    @GetMapping("/favorites")
    public Result<PageResult<PostVO>> favorites(@RequestParam(required = false, defaultValue = "1") Integer page,
                                                @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(userService.favorites(page, size));
    }

    @Operation(summary = "签到日历（BitMap 查询）")
    @GetMapping("/{id}/sign")
    public Result<Map<String, Object>> signCalendar(@PathVariable Long id,
                                                    @RequestParam(required = false) String month) {
        return Result.ok(userService.signCalendar(id, month));
    }

    @Operation(summary = "关注/取消关注用户（幂等切换）🔒")
    @PostMapping("/{id}/follow")
    public Result<Map<String, Object>> follow(@PathVariable Long id) {
        return Result.ok(userService.follow(id));
    }
}
