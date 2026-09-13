package com.dbd.controller;

import com.dbd.common.PageResult;
import com.dbd.common.Result;
import com.dbd.dto.ActivityCreateDTO;
import com.dbd.dto.BarCreateDTO;
import com.dbd.dto.NoticeCreateDTO;
import com.dbd.service.AdminService;
import com.dbd.vo.ActivityVO;
import com.dbd.vo.BarVO;
import com.dbd.vo.PostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理接口（对应 API.md §3.9）。
 *
 * <p>整个 {@code /api/admin/**} 由
 * {@link com.dbd.interceptor.AdminInterceptor} 统一要求 role=1，
 * 因此本类内无需重复做权限校验。</p>
 *
 * <p>语义区分：<b>隐藏</b>=改状态可恢复；<b>删除</b>=物理删除不可恢复。</p>
 */
@Tag(name = "管理模块")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /* ==================== 帖子管理 ==================== */

    @Operation(summary = "帖子管理列表 🔒管理员（含隐藏/精华等全部状态）")
    @GetMapping("/post/list")
    public Result<PageResult<PostVO>> postList(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) Integer type,
                                               @RequestParam(required = false, defaultValue = "1") Integer page,
                                               @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.postList(keyword, status, type, page, size));
    }

    @Operation(summary = "隐藏帖子 🔒管理员（status=3，前台全链路不可见，可恢复）")
    @PostMapping("/post/{id}/hide")
    public Result<Void> hidePost(@PathVariable Long id) {
        adminService.hidePost(id);
        return Result.ok(null, "已隐藏");
    }

    @Operation(summary = "恢复帖子 🔒管理员（status=1）")
    @PostMapping("/post/{id}/restore")
    public Result<Void> restorePost(@PathVariable Long id) {
        adminService.restorePost(id);
        return Result.ok(null, "已恢复");
    }

    @Operation(summary = "删除帖子 🔒管理员（物理删除，级联清理楼层/点赞/收藏，不可恢复）")
    @DeleteMapping("/post/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        adminService.deletePost(id);
        return Result.ok(null, "已删除");
    }

    @Operation(summary = "置顶帖子 🔒管理员（全站生效，可置顶他人的帖子，is_top=1）")
    @PostMapping("/post/{id}/top")
    public Result<Void> topPost(@PathVariable Long id) {
        adminService.topPost(id);
        return Result.ok(null, "已置顶");
    }

    @Operation(summary = "取消置顶 🔒管理员（is_top=0）")
    @PostMapping("/post/{id}/untop")
    public Result<Void> untopPost(@PathVariable Long id) {
        adminService.untopPost(id);
        return Result.ok(null, "已取消置顶");
    }

    /* ==================== 公告 ==================== */

    @Operation(summary = "发布官方公告 🔒管理员（公告本身是一条帖子：不挂吧、恒置顶、全站可见）")
    @PostMapping("/notice")
    public Result<Map<String, Object>> publishNotice(@Valid @RequestBody NoticeCreateDTO dto) {
        Long id = adminService.publishNotice(dto);
        // id 为 18-19 位 Long，转字符串避免前端 JS 精度丢失（与发帖接口一致）
        return Result.ok(Map.of("id", String.valueOf(id)), "公告已发布");
    }

    /* ==================== 吧管理 ==================== */

    @Operation(summary = "吧管理列表 🔒管理员（含已隐藏的吧）")
    @GetMapping("/bar/list")
    public Result<PageResult<BarVO>> barList(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(required = false, defaultValue = "1") Integer page,
                                             @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.barList(keyword, status, page, size));
    }

    @Operation(summary = "创建贴吧 🔒管理员（名称唯一）")
    @PostMapping("/bar")
    public Result<Map<String, Object>> createBar(@Valid @RequestBody BarCreateDTO dto) {
        Long id = adminService.createBar(dto);
        // 同上：新建吧的 ID 会回传前端用于发帖等后续操作，必须按字符串传输
        return Result.ok(Map.of("id", String.valueOf(id)), "创建成功");
    }

    @Operation(summary = "隐藏贴吧 🔒管理员（status=0，前台不可见且移出热吧榜）")
    @PostMapping("/bar/{id}/hide")
    public Result<Void> hideBar(@PathVariable Long id) {
        adminService.hideBar(id);
        return Result.ok(null, "已隐藏");
    }

    @Operation(summary = "恢复贴吧 🔒管理员（status=1）")
    @PostMapping("/bar/{id}/restore")
    public Result<Void> restoreBar(@PathVariable Long id) {
        adminService.restoreBar(id);
        return Result.ok(null, "已恢复");
    }

    @Operation(summary = "删除贴吧 🔒管理员（物理删除，级联删除其下全部帖子，不可恢复）")
    @DeleteMapping("/bar/{id}")
    public Result<Void> deleteBar(@PathVariable Long id) {
        adminService.deleteBar(id);
        return Result.ok(null, "已删除");
    }

    /* ==================== 限量徽章活动管理 ==================== */

    @Operation(summary = "活动管理列表 🔒管理员（含未开始/已结束，剩余库存实时）")
    @GetMapping("/activity/list")
    public Result<PageResult<ActivityVO>> activityList(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(required = false, defaultValue = "1") Integer page,
                                                       @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.activityList(keyword, status, page, size));
    }

    @Operation(summary = "发布限量徽章活动 🔒管理员（称号全局唯一）")
    @PostMapping("/activity")
    public Result<Map<String, Object>> createActivity(@Valid @RequestBody ActivityCreateDTO dto) {
        Long id = adminService.createActivity(dto);
        // 同创建贴吧：新活动 ID 会回传前端用于跳转，必须按字符串传输避免 JS 精度丢失
        return Result.ok(Map.of("id", String.valueOf(id)), "发布成功");
    }

    @Operation(summary = "编辑限量徽章活动 🔒管理员（保持已抢数量不变）")
    @PutMapping("/activity/{id}")
    public Result<Void> updateActivity(@PathVariable Long id, @Valid @RequestBody ActivityCreateDTO dto) {
        adminService.updateActivity(id, dto);
        return Result.ok(null, "已保存");
    }

    @Operation(summary = "提前结束活动 🔒管理员（已抢到的徽章保留）")
    @PostMapping("/activity/{id}/end")
    public Result<Void> endActivity(@PathVariable Long id) {
        adminService.endActivity(id);
        return Result.ok(null, "已结束");
    }

    @Operation(summary = "删除活动 🔒管理员（物理删除，领取记录与徽章一并失效，不可恢复）")
    @DeleteMapping("/activity/{id}")
    public Result<Void> deleteActivity(@PathVariable Long id) {
        adminService.deleteActivity(id);
        return Result.ok(null, "已删除");
    }
}
