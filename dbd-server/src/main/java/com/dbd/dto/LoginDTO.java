package com.dbd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 验证码登录请求体（对应 API.md §3.1.2）。
 */
@Data
public class LoginDTO {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 验证码（6 位数字） */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    /** 昵称（仅首次登录注册时使用，不传则后端随机生成） */
    private String nickname;
}
