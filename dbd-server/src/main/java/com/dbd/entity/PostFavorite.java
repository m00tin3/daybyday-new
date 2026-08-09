package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子收藏实体，对应表 {@code post_favorite}。
 * <p>Redis Set {@code dbd:post:favorite:{postId}} 为准，本表同步落库兜底（唯一索引防重）。</p>
 */
@Data
@TableName("post_favorite")
public class PostFavorite {

    @TableId
    private Long id;

    private Long postId;

    private Long userId;

    private LocalDateTime createdAt;
}
