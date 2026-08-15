package com.dbd.vo;

import com.dbd.entity.Activity;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 活动视图对象（对应 API.md §2.5 ActivityVO）。
 */
@Data
public class ActivityVO {

    private Long id;
    private Long barId;
    private String title;
    private Integer type;
    private Integer stock;
    /** 剩余库存（Redis 实时） */
    private Integer remainStock;
    private String awardDesc;
    private String beginTime;
    private String endTime;
    private Integer status;
    /** 当前用户是否已抢/已领（未登录 false） */
    private Boolean grabbed;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static ActivityVO from(Activity a) {
        ActivityVO vo = new ActivityVO();
        vo.setId(a.getId());
        vo.setBarId(a.getBarId());
        vo.setTitle(a.getTitle());
        vo.setType(a.getType());
        vo.setStock(a.getStock());
        vo.setAwardDesc(a.getAwardDesc());
        vo.setBeginTime(a.getBeginTime() == null ? null : a.getBeginTime().format(FMT));
        vo.setEndTime(a.getEndTime() == null ? null : a.getEndTime().format(FMT));
        vo.setStatus(a.getStatus());
        vo.setGrabbed(false);
        return vo;
    }
}
