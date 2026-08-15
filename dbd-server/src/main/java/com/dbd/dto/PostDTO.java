package com.dbd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发帖请求体（对应 API.md §3.2.3）。
 */
@Data
public class PostDTO {

    @NotNull(message = "请选择所属吧")
    private Long barId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题最长 64 字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 50000, message = "内容最长 50000 字符")
    private String content;

    /** 图片URL列表（可空） */
    private List<String> images;

    /** 经度（可选，带坐标时写入 GEO 同城） */
    private Double x;

    /** 纬度（可选，带坐标时写入 GEO 同城） */
    private Double y;
}
