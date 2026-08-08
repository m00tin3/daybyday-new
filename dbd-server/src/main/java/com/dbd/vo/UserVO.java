package com.dbd.vo;

import com.dbd.entity.User;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 用户视图对象（对应 API.md §2.1 UserVO）。
 * <p>注意：不返回 phone（脱敏）。</p>
 */
@Data
public class UserVO {

    private Long id;

    private String nickname;

    private String icon;

    private String signText;

    private String createdAt;

    /** entity → VO（时间统一 yyyy-MM-dd HH:mm:ss） */
    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setIcon(user.getIcon());
        vo.setSignText(user.getSignText());
        vo.setCreatedAt(user.getCreatedAt() == null ? null
                : user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return vo;
    }
}
