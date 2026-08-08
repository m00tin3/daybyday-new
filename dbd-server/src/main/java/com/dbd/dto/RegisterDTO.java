package com.dbd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 显式注册请求体（对应 API.md §3.1.3，可选实现）。
 */
@Data
public class RegisterDTO {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 验证码（6 位数字） */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    /** 昵称（2-16 字符） */
    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 16, message = "昵称长度为 2-16 字符")
    private String nickname;
}
