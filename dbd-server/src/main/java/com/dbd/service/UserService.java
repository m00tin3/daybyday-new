package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.dto.UpdateProfileDTO;
import com.dbd.vo.PostVO;
import com.dbd.vo.UserProfileVO;
import com.dbd.vo.UserVO;

import java.util.Map;

/**
 * 用户服务：主页信息 / 我的帖子 / 收藏 / 签到日历 / 关注用户 / 资料修改（对应 API.md §3.4）。
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

    /** 修改个人资料（仅本人；字段为 null 表示不改动），返回更新后的用户信息 */
    UserVO updateProfile(UpdateProfileDTO dto);

    /** 是否管理员（带 Redis 缓存，管理接口鉴权用） */
    boolean isAdmin(Long userId);
}
