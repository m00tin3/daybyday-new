package com.dbd.controller;

import com.dbd.common.Result;
import com.dbd.service.FeedService;
import com.dbd.vo.FeedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关注 Feed 流接口（对应 API.md §3.5.2，阶段三）。
 */
@Tag(name = "关注 Feed 流")
@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @Operation(summary = "关注时间线滚动分页 🔒（未登录返回 2003）")
    @GetMapping
    public Result<FeedResult> feed(@RequestParam(required = false) Long lastId,
                                   @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(feedService.feed(lastId, size));
    }
}
