package com.dbd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建贴吧请求体（对应 POST /api/admin/bar）。
 */
@Data
public class BarCreateDTO {

    /** 吧名称（全局唯一，重复会返回明确提示） */
    @NotBlank(message = "吧名称不能为空")
    @Size(min = 1, max = 32, message = "吧名称长度需在 1-32 字之间")
    private String name;

    /** 吧简介 */
    @Size(max = 255, message = "吧简介不能超过 255 字")
    private String description;

    /** 封面图 URL */
    @Size(max = 255, message = "封面 URL 不能超过 255 字符")
    private String cover;
}
