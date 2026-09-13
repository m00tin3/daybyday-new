package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知实体，对应表 {@code notification}。
 *
 * <p>把「回复我的帖子 / 回复我的楼层 / 赞了我的帖子」统一封装成一条消息，
 * 用 {@link #type} 区分究竟是哪种 —— 前端列表里显示"您有一条新消息，
 * 点开看是回复还是点赞"。</p>
 *
 * <p><b>未读数刻意走 DB 的 {@code COUNT(*)}，不设 Redis 计数器。</b>
 * 点赞计数刚出过"Redis 与 DB 双写不一致导致前台恒为 0"的事故，
 * 未读数是低频读取（只在导航上显示一个红点），不值得再引入一处双写状态。</p>
 */
@Data
@TableName("notification")
public class Notification {

    /** 类型：有人回复了我的帖子 */
    public static final int TYPE_REPLY_POST = 1;

    /** 类型：有人回复了我的楼层（楼中楼） */
    public static final int TYPE_REPLY_COMMENT = 2;

    /** 类型：有人赞了我的帖子 */
    public static final int TYPE_LIKE_POST = 3;

    /** 通知ID（全局ID生成器） */
    @TableId
    private Long id;

    /** 接收者ID */
    private Long userId;

    /** 类型，取值见 {@link #TYPE_REPLY_POST} / {@link #TYPE_REPLY_COMMENT} / {@link #TYPE_LIKE_POST} */
    private Integer type;

    /** 触发者ID */
    private Long fromUserId;

    /** 相关帖子ID */
    private Long postId;

    /** 相关楼层ID（点赞类通知为 null） */
    private Long commentId;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}
