package com.dbd.vo;

import com.dbd.entity.ActivityOrder;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 秒杀订单视图对象（对应 API.md §2.6 ActivityOrderVO）。
 */
@Data
public class ActivityOrderVO {

    private Long id;
    private Long activityId;
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
