package com.dbd.vo;

import com.dbd.entity.ActivityOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 秒杀订单视图对象（对应 API.md §2.6 ActivityOrderVO）。
 */
@Data
public class ActivityOrderVO {

    /** 订单ID：序列化为字符串，避免 JS 大整数精度丢失（见 {@link UserVO#getId()} 说明） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 活动ID：标识类字段 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 用户ID：标识类字段 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private Integer status;
    private String createdAt;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static ActivityOrderVO from(ActivityOrder o) {
        ActivityOrderVO vo = new ActivityOrderVO();
        vo.setId(o.getId());
        vo.setActivityId(o.getActivityId());
        vo.setUserId(o.getUserId());
        vo.setStatus(o.getStatus());
        vo.setCreatedAt(o.getCreatedAt() == null ? null : o.getCreatedAt().format(FMT));
        return vo;
    }
}
