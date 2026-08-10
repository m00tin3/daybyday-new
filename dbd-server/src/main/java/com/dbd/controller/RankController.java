package com.dbd.controller;

import com.dbd.common.Result;
import com.dbd.service.RankService;
import com.dbd.vo.PostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排行接口（对应 API.md §3.5）。
 */
@Tag(name = "排行模块")
@RestController
@RequestMapping("/api/rank")
public class RankController {

    private final RankService rankService;

    public RankController(RankService rankService) {
        this.rankService = rankService;
    }

    @Operation(summary = "热帖榜（ZSet，最多 20）")
    @GetMapping("/hot/post")
    public Result<List<PostVO>> hotPosts() {
        return Result.ok(rankService.hotPosts());
    }
}
