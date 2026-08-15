package com.dbd.service;

import com.dbd.vo.ActivityVO;

import java.util.Map;

/**
 * 秒杀服务：活动详情 / 抢楼·领徽章（对应 API.md §3.6）。
 */
public interface ActivityService {

    /** 活动详情：剩余库存实时读 Redis + 当前用户是否已抢 */
    ActivityVO detail(Long id);

    /** 抢楼/领取徽章：Lua 原子预扣库存 + 一人一单，返回 { orderId, floorNo } */
    Map<String, Object> grab(Long id);
}
