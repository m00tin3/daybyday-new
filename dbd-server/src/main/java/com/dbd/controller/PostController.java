package com.dbd.controller;

import com.dbd.common.PageResult;
import com.dbd.common.Result;
import com.dbd.dto.CommentDTO;
import com.dbd.dto.PostDTO;
import com.dbd.service.PostService;
import com.dbd.vo.CityStatVO;
import com.dbd.vo.CommentVO;
import com.dbd.vo.PostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 帖子接口（对应 API.md §3.2）。
 */
@Tag(name = "帖子模块")
@RestController
@RequestMapping("/api/post")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "帖子列表（可按吧/用户/城市/关键词筛选，分页）")
    @GetMapping("/list")
    public Result<PageResult<PostVO>> list(@RequestParam(required = false) Long barId,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) String city,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false, defaultValue = "1") Integer page,
                                           @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(postService.page(barId, userId, city, keyword, page, size));
    }

    @Operation(summary = "有帖子的城市列表（按帖子数降序，用于「按城市浏览」）")
    @GetMapping("/cities")
    public Result<List<CityStatVO>> cities() {
        return Result.ok(postService.cities());
    }

    @Operation(summary = "帖子详情（缓存三件套 + UV/浏览计数）")
    @GetMapping("/{id}")
    public Result<PostVO> detail(@PathVariable Long id) {
        return Result.ok(postService.detail(id));
    }

    @Operation(summary = "发帖 🔒")
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody PostDTO dto) {
        Long id = postService.create(dto);
        // id 必须按字符串下发：Redis 全局 ID 为 18-19 位 Long，超出 JS 安全整数范围
        // （Number.MAX_SAFE_INTEGER 仅 16 位）。若以数字传输，前端
        // router.push(`/post/${id}`) 会拿到被四舍五入的错误 ID，跳到不存在的帖子
        return Result.ok(Map.of("id", String.valueOf(id)), "发布成功");
    }

    @Operation(summary = "点赞/取消点赞（幂等切换）🔒")
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        return Result.ok(postService.like(id));
    }

    @Operation(summary = "收藏/取消收藏（幂等切换）🔒")
    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id) {
        return Result.ok(postService.favorite(id));
    }

    @Operation(summary = "楼层列表（分页，按楼层号升序）")
    @GetMapping("/{id}/comments")
    public Result<PageResult<CommentVO>> comments(@PathVariable Long id,
                                                  @RequestParam(required = false, defaultValue = "1") Integer page,
                                                  @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(postService.comments(id, page, size));
    }

    @Operation(summary = "某一层楼的子回复分页（楼中楼翻页）")
    @GetMapping("/{id}/comment/{floorId}/replies")
    public Result<PageResult<CommentVO>> floorReplies(@PathVariable Long id,
                                                      @PathVariable Long floorId,
                                                      @RequestParam(required = false, defaultValue = "1") Integer page,
                                                      @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(postService.floorReplies(id, floorId, page, size));
    }

    @Operation(summary = "回帖/盖楼 🔒")
    @PostMapping("/{id}/comment")
    public Result<Map<String, Object>> addComment(@PathVariable Long id, @Valid @RequestBody CommentDTO dto) {
        return Result.ok(postService.addComment(id, dto), "盖楼成功");
    }
}
