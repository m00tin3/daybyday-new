package com.dbd.vo;

import com.dbd.entity.User;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 当前登录用户的完整资料（**仅返回给用户本人**）。
 *
 * <p>与 {@link UserVO} 的关键区别：本 VO 包含 {@code phone}。</p>
 * <p>{@code UserVO} 会随帖子作者、评论者、吧成员等场景展示给其他用户，
 * 因此不能携带手机号；只有"查自己"的接口才使用本 VO。</p>
 */
@Data
public class UserSelfVO {

    /** 用户ID：序列化为字符串，避免 JS 大整数精度丢失（见 {@link UserVO#getId()} 说明） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String nickname;

    private String icon;

    private String signText;

    /** 登录账号（手机号或管理员标识）：仅本人可见 */
    private String phone;

    /** 角色 0普通用户 1管理员 */
    private Integer role;

    private String createdAt;

    public static UserSelfVO from(User user) {
        UserSelfVO vo = new UserSelfVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setIcon(user.getIcon());
        vo.setSignText(user.getSignText());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole() == null ? User.ROLE_USER : user.getRole());
        vo.setCreatedAt(user.getCreatedAt() == null ? null
                : user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return vo;
    }
}
