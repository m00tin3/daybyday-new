package com.dbd.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知列表联表查询行（join user/post/comment 补全展示字段），SQL 见 NotificationMapper.xml。
 */
@Data
public class NotificationRow {

    private Long id;
    private Integer type;
    private Long fromUserId;
    private Long postId;
    private Long commentId;
    private Integer isRead;
    private LocalDateTime createdAt;

    /** 触发者昵称（join user；用户理论上不会被物理删除，但仍按可空处理） */
    private String fromNickname;

    /** 触发者头像（join user） */
    private String fromIcon;

    /** 帖子标题（join post；帖子被删则为 null） */
    private String postTitle;

    /** 回复内容摘要（join comment，SQL 侧截断；点赞类为 null） */
    private String commentSnippet;
}
