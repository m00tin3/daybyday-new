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

    /** 楼层号（1 起，帖内递增，Redis INCR 生成） */
    private Integer floorNo;

    /** 内容 */
    private String content;

    /** 图片URL列表（JSON数组字符串） */
    private String images;

    /** 楼中楼父楼层ID（null=直接回帖） */
    private Long parentId;

    /** 点赞数（Redis 为准） */
    private Integer likeCount;

    /** 状态 1正常 0删除 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
