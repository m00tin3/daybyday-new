package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注关系实体，对应表 {@code follow}（用户-用户 / 用户-吧）。
 */
@Data
@TableName("follow")
public class Follow {

    @TableId
    private Long id;

    /** 粉丝ID */
    private Long userId;

    /** 关注的用户ID（type=1 时） */
    private Long followUserId;

    /** 关注的吧ID（type=2 时） */
    private Long followBarId;

    /** 类型 1关注用户 2关注吧 */
    private Integer followType;

    private LocalDateTime createdAt;
}
