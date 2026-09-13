package com.dbd.vo;

import com.dbd.entity.Notification;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 通知视图对象（对应 API.md §3.10）。
 */
@Data
public class NotificationVO {

    /** 通知ID：序列化为字符串，避免 JS 大整数精度丢失（见 {@link UserVO#getId()} 说明） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 类型 1回复帖子 2回复楼层 3赞帖子 */
    private Integer type;

    /**
     * 类型的中文描述（"回复了你的帖子" / "回复了你" / "赞了你的帖子"）。
     * <p>由后端算好，前端直接渲染 —— 避免前端再维护一份类型映射表。</p>
     */
    private String typeText;

    /** 触发者（含昵称/头像/徽章） */
    private UserVO fromUser;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long postId;

    /** 帖子标题；**帖子已被删除时为 null**，前端显示「帖子已删除」而不是让整条通知消失 */
    private String postTitle;

    /** 相关楼层ID（点赞类为 null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long commentId;

    /** 回复内容摘要（点赞类为 null） */
    private String contentSnippet;

    private Boolean isRead;

    private String createdAt;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static NotificationVO fromRow(NotificationRow row) {
        NotificationVO vo = new NotificationVO();
        vo.setId(row.getId());
        vo.setType(row.getType());
        vo.setTypeText(textOf(row.getType()));
        UserVO from = new UserVO();
        from.setId(row.getFromUserId());
        from.setNickname(row.getFromNickname());
        from.setIcon(row.getFromIcon());
        vo.setFromUser(from);
        vo.setPostId(row.getPostId());
        vo.setPostTitle(row.getPostTitle());
        vo.setCommentId(row.getCommentId());
        vo.setContentSnippet(row.getCommentSnippet());
        vo.setIsRead(row.getIsRead() != null && row.getIsRead() == 1);
        vo.setCreatedAt(row.getCreatedAt() == null ? null : row.getCreatedAt().format(FMT));
        return vo;
    }

    private static String textOf(Integer type) {
        if (type == null) {
            return "有新消息";
        }
        return switch (type) {
            case Notification.TYPE_REPLY_POST -> "回复了你的帖子";
            case Notification.TYPE_REPLY_COMMENT -> "回复了你";
            case Notification.TYPE_LIKE_POST -> "赞了你的帖子";
            default -> "有新消息";
        };
    }
}
