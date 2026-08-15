package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀订单/领取记录实体，对应表 {@code activity_order}。
 * <p>唯一索引 {@code uk_activity_user(activity_id, user_id)} 为「一人一单」的 DB 双保险。</p>
 */
@Data
@TableName("activity_order")
public class ActivityOrder {

    /** 订单ID（全局ID生成器） */
    @TableId
    private Long id;

    /** 活动ID */
    private Long activityId;

    /** 用户ID */
    private Long userId;

    /** 状态 0待领取 1已领取 2已取消 */
    private Integer status;

    /** 业务单号 */
    private String orderNo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
