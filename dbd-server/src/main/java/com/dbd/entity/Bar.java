package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 吧实体，对应表 {@code bar}。
 */
@Data
@TableName("bar")
public class Bar {

    @TableId
    private Long id;

    private String name;

    private String description;

    private String cover;

    private Long creatorId;

    private Integer memberCount;

    private Integer postCount;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
