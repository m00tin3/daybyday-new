package com.dbd.controller;

import com.dbd.common.PageResult;
import com.dbd.common.Result;
import com.dbd.service.NotificationService;
import com.dbd.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消息通知接口（对应 API.md §3.10）。
 *
 * <p>路径 {@code /api/notification/**} 被 LoginInterceptor 按 {@code /api/**} 自动纳入：
 * GET 匿名放行、写操作强制登录，因此这里不需要在 WebConfig 里额外注册。</p>
 */
@Tag(name = "消息通知")
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "我的通知列表 🔒（时间倒序，含触发者/帖子标题/内容摘要）")
    @GetMapping
    public Result<PageResult<NotificationVO>> list(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(notificationService.list(page, size));
    }

    @Operation(summary = "未读数（未登录返回 0，仅供导航红点）")
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        return Result.ok(Map.of("count", notificationService.unreadCount()));
    }

    @Operation(summary = "全部标记为已读 🔒（前端进通知页时调用）")
    @PostMapping("/read-all")
    public Result<Void> readAll() {
        notificationService.readAll();
        return Result.ok(null, "已全部标为已读");
    }
}
