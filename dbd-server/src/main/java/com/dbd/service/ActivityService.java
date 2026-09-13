package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.vo.ActivityVO;
import com.dbd.vo.BadgeVO;

import java.util.List;
import java.util.Map;

/**
 * 秒杀服务：活动列表 / 活动详情 / 抢楼·领徽章 / 徽章墙（对应 API.md §3.6）。
 */
public interface ActivityService {

    /**
     * 活动列表（前台活动广场）。
     *
     * @param type 活动类型 1抢楼 2限量徽章；为 null 表示不限
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     * @return 按开始时间倒序分页；每条都带实时剩余库存与当前用户是否已抢
     */
    PageResult<ActivityVO> list(Integer type, Integer page, Integer size);

    /** 活动详情：剩余库存实时读 Redis + 当前用户是否已抢 */
    ActivityVO detail(Long id);

    /** 抢楼/领取徽章：Lua 原子预扣库存 + 一人一单，返回 { orderId, floorNo } */
    Map<String, Object> grab(Long id);

    /** 当前登录用户的徽章墙 */
    List<BadgeVO> myBadges();

    /** 指定用户的徽章墙（公开，用于他人主页） */
    List<BadgeVO> userBadges(Long userId);

    /**
     * 把一批活动实体补齐成 VO：动态状态 + Redis 实时剩余库存 + 已抢数量 + 当前用户是否已抢。
     *
     * <p>库存值走<b>一次 MGET</b> 批量取，不在循环里逐个 GET——否则一页 12 条活动
     * 就是 12 次 Redis 往返。</p>
     *
     * <p>供管理端列表复用，保证前后台展示口径一致。</p>
     */
    List<ActivityVO> toVOList(List<com.dbd.entity.Activity> activities);
}
