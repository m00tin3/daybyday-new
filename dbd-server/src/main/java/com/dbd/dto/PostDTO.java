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

    /**
     * 城市（可选）：发帖时手动填写，用于"按城市浏览"。
     *
     * <p>原先这里是可选的经纬度（GEO 同城）。因缺少地图 SDK 无法把用户输入的
     * 地址转换成坐标，要求手输经纬度体验极差且无法校验，故改为城市字段；
     * GEO 相关代码保留但功能已封存。</p>
     */
    @Size(max = 32, message = "城市名最长 32 字符")
    private String city;
}
