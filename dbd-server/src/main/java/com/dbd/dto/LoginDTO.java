package com.dbd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 验证码登录请求体（对应 API.md §3.1.2）。
 * <p>注意：{@code phone} 同时承载普通用户的手机号与管理员标识（如 2485617328），
 * 因此校验放宽为 6-20 位数字，不再限定 11 位手机号格式。</p>
 */
@Data
public class LoginDTO {

    /** 登录账号：手机号（11 位）或管理员标识 */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^\\d{6,20}$", message = "账号格式不正确（6-20 位数字）")
    private String phone;

    /** 验证码（6 位数字；管理员账号在开启免验证码登录时任意 6 位数字均可） */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    /** 昵称（仅首次登录注册时使用，不传则后端随机生成） */
    private String nickname;
}
