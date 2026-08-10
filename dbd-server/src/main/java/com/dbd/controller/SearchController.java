package com.dbd.controller;

import com.dbd.common.PageResult;
import com.dbd.common.Result;
import com.dbd.service.SearchService;
import com.dbd.vo.PostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索接口（对应 API.md §3.7）。
 */
@Tag(name = "搜索模块")
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "热搜词（ZSet，最多 10）")
    @GetMapping("/hot")
    public Result<List<String>> hot() {
        return Result.ok(searchService.hot());
    }

    @Operation(summary = "搜索帖子")
    @GetMapping("/post")
    public Result<PageResult<PostVO>> search(@RequestParam String keyword,
                                             @RequestParam(required = false, defaultValue = "1") Integer page,
                                             @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(searchService.search(keyword, page, size));
    }
}
