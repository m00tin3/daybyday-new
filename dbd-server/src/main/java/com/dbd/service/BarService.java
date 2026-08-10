package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.vo.BarVO;
import com.dbd.vo.PostVO;

import java.util.List;
import java.util.Map;

/**
 * 吧服务：吧信息缓存 / 吧内帖子 / BitMap 签到 / 关注 / 热吧榜（对应 API.md §3.3）。
 */
public interface BarService {

    /** 吧信息（缓存 + 请求级状态 isFollowed/signedToday/memberCount 实时） */
    BarVO info(Long id);

    /** 吧内帖子（复用帖子模块分页） */
    PageResult<PostVO> posts(Long id, Integer page, Integer size);

    /** 吧签到（BitMap + BITFIELD），返回 { signedDays 连续, signCount 本月累计, award } */
    Map<String, Object> sign(Long id);

    /** 关注/取消关注吧（幂等切换），返回 { isFollowed, memberCount } */
    Map<String, Object> follow(Long id);

    /** 热吧榜（ZSet，最多 10） */
    List<BarVO> rank();

    /** 定时重算热吧榜（DB member_count → ZSet） */
    void rebuildRank();
}
