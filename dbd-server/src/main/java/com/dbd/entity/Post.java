package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 帖子实体，对应表 {@code post}。
 */
@Data
@TableName("post")
public class Post {

    /** 帖子ID（全局ID生成器） */
    @TableId
    private Long id;

    /** 所属吧ID */
    private Long barId;

    /** 发帖人ID */
    private Long userId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 图片URL列表（JSON数组字符串） */
    private String images;

    /** 状态 1正常 0删除 2精华 */
    private Integer status;

    /** 是否置顶 0否 1是 */
    private Integer isTop;

    /** 点赞数（Redis 为准，异步落库） */
    private Integer likeCount;

    /** 收藏数（Redis 为准，异步落库） */
    private Integer favoriteCount;

    /** 楼层数（Redis 为准，异步落库） */
    private Integer commentCount;

    /** 浏览量（Redis 为准，异步落库） */
    private Integer viewCount;

    /** 独立访客数（HyperLogLog，定时落库） */
    private Integer uvCount;

    /** 热度分（ZSet 排行用，定时重算） */
    private BigDecimal score;

    /** 最后回复时间（列表排序用） */
    private LocalDateTime lastCommentTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
