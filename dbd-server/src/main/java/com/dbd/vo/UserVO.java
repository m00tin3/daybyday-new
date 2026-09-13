package com.dbd.vo;

import com.dbd.entity.User;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 用户视图对象（对应 API.md §2.1 UserVO）。
 * <p>注意：不返回 phone（脱敏）、不返回 password。</p>
 */
@Data
public class UserVO {

    /**
     * 用户ID —— **序列化为 JSON 字符串**。
     *
     * <p>原因：本项目主键由 Redis 全局 ID 生成器产出（时间戳+自增，18-19 位），
     * 超过 JavaScript 的 {@code Number.MAX_SAFE_INTEGER}（2^53-1，16 位）。
     * 若按 JSON 数字输出，浏览器 {@code JSON.parse} 会四舍五入导致 ID 错位
     * （例如 …193 变成 …192），表现为"选中了吧却提示吧不存在"这类问题。</p>
     *
     * <p>因此**标识类** Long 字段一律用 ToStringSerializer；**计数类**字段保持数字，
     * 避免影响前端数值运算与分页组件。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String nickname;

    private String icon;

    private String signText;

    private String createdAt;

    /** 角色 0普通用户 1管理员（前端据此显示"管理后台"入口；最终鉴权仍由后端负责） */
    private Integer role;

    /**
     * 已获得的限量徽章称号（如 ["凤川祥", "千早樱"]）。
     * <p>由 {@link com.dbd.service.BadgeService} 在列表组装完成后**批量填充**，
     * 不在 {@link #from(User)} 里查库——否则每个作者一次 SQL 就是 N+1。
     * 应用场景：帖子/楼层的作者昵称旁挂角标。</p>
     */
    private List<String> badges;

    /** entity → VO（时间统一 yyyy-MM-dd HH:mm:ss） */
    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setIcon(user.getIcon());
        vo.setSignText(user.getSignText());
        vo.setRole(user.getRole() == null ? User.ROLE_USER : user.getRole());
        vo.setCreatedAt(user.getCreatedAt() == null ? null
                : user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return vo;
    }
}
