package com.dbd.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料修改请求体（对应 PUT /api/user/profile）。
 * <p>字段全部可选：只提交需要修改的字段，{@code null} 表示不改动该字段。</p>
 */
@Data
public class UpdateProfileDTO {

    /** 昵称 */
    @Size(min = 1, max = 32, message = "昵称长度需在 1-32 字之间")
    private String nickname;

    /** 个性签名 */
    @Size(max = 128, message = "个性签名不能超过 128 字")
    private String signText;

    /**
     * 头像 URL。
     * <p>说明：项目暂无文件上传与对象存储，因此先支持直接填写图片地址；
     * 后续若接入上传，此处改为接收上传接口返回的 URL 即可。</p>
     */
    @Size(max = 255, message = "头像 URL 不能超过 255 字符")
    private String icon;

    /** 登录账号（手机号或管理员标识）：改动时需保证全局唯一 */
    @Pattern(regexp = "^\\d{6,20}$", message = "账号格式不正确（6-20 位数字）")
    private String phone;
}
