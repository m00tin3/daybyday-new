package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.vo.PostVO;
import com.dbd.vo.UserProfileVO;

import java.util.Map;

/**
 * 用户服务：主页信息 / 我的帖子 / 收藏 / 签到日历 / 关注用户（对应 API.md §3.4）。
 */
public interface UserService {

    /** 用户主页信息 */
    UserProfileVO profile(Long id);

    /** 用户帖子（分页，复用帖子模块） */
    PageResult<PostVO> posts(Long id, Integer page, Integer size);

    /** 我的收藏（仅本人可查） */
    PageResult<PostVO> favorites(Integer page, Integer size);

    /** 签到日历：month 为 yyyyMM，默认当月 */
    Map<String, Object> signCalendar(Long userId, String month);

    /** 关注/取消关注用户（幂等切换，不可关注自己） */
    Map<String, Object> follow(Long id);
}
