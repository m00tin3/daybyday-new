package com.dbd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布公告请求体（对应 API.md §3.9.16）。
 *
 * <p>刻意不复用 {@link PostDTO}：那个 DTO 的 {@code barId} 是 {@code @NotNull}，
 * 为了发公告而放开它，普通用户就能自己发出一条 {@code type=1} 的"公告"（提权）。
 * 公告只能从 {@code /api/admin/notice} 走，由 AdminInterceptor 强制 role=1。</p>
 *
 * <p>公告不挂吧（{@code bar_id = NULL}）、不填城市，发布后自动置顶全站。</p>
 */
@Data
public class NoticeCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题最长 64 字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 50000, message = "内容最长 50000 字符")
    private String content;
}
