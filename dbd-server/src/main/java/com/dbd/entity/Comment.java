package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 楼层实体，对应表 {@code comment}。
 */
@Data
@TableName("`comment`")
public class Comment {

    /** 楼层ID（全局ID生成器） */
    @TableId
    private Long id;

    /** 帖子ID */
    private Long postId;

    /** 回复人ID */
    private Long userId;

    /** 楼层号（1 起，帖内递增，Redis INCR 生成；楼中楼固定 0，不占楼层号） */
    private Integer floorNo;

    /** 内容 */
    private String content;

    /** 图片URL列表（JSON数组字符串） */
    private String images;

    /** 楼中楼父楼层ID（null=直接回帖） */
    private Long parentId;

    /**
     * 被回复者ID（仅楼中楼有意义）。
     *
     * <p>两层结构下 {@link #parentId} 只指向顶层楼层，而用户可能是点某条**子回复**
     * 的「回复」——此时光看 parentId 只知道"回复了 1 楼"，不知道"回复的是 1 楼里那个人"。
     * 这一列就是被点的那条评论的作者，用于决定通知发给谁、以及前端显示「回复 @某某」。</p>
     *
     * <p>为 null 表示"回复的就是楼主层本身"，此时被回复者等于父楼层的作者。</p>
     */
    private Long replyToUserId;

    /** 点赞数（Redis 为准） */
    private Integer likeCount;

    /** 状态 1正常 0删除 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
