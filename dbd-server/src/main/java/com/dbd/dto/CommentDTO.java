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

    /** 楼中楼父楼层ID（不传为直接回帖） */
    private Long parentId;
}
