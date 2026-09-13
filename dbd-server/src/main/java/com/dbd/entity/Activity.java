package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动实体，对应表 {@code activity}（抢楼 / 限量徽章）。
 */
@Data
@TableName("activity")
public class Activity {

    /** 活动ID（全局ID生成器） */
    @TableId
    private Long id;

    /** 关联吧ID（抢楼活动所在吧，可空） */
    private Long barId;

    /** 活动标题 */
    private String title;

    /** 类型 1抢楼 2限量徽章 */
    private Integer type;

    /** 限量徽章称号（type=2 时使用，如「凤川祥」；抢楼活动为 null） */
    private String badgeName;

    /** 总库存（徽章数量 / 楼层上限） */
    private Integer stock;

    /** 奖励描述 */
    private String awardDesc;

    /** 开始时间 */
    private LocalDateTime beginTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态 0未开始 1进行中 2已结束 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
