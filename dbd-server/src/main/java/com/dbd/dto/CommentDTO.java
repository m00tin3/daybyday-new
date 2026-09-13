package com.dbd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回帖请求体（对应 API.md §3.2.7）。
 */
@Data
public class CommentDTO {

    @NotBlank(message = "内容不能为空")
    @Size(max = 2048, message = "内容最长 2048 字符")
    private String content;

    /** 楼中楼父楼层ID（不传为直接回帖）。二层结构下必须是**顶层**楼层 */
    private Long parentId;

    /**
     * 被回复的那条评论ID（可选）。
     *
     * <p>与 {@link #parentId} 的分工：parentId 决定"挂在哪个顶层楼层下"（结构），
     * 本字段决定"回复的是谁"（通知对象 + 前端显示「回复 @某某」）。
     * 典型用法：parentId 传顶层楼层，replyToCommentId 传被点的那条子回复。
     * 不传则视为回复顶层楼层本身。</p>
     */
    private Long replyToCommentId;
}
