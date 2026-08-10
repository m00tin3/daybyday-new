package com.dbd.vo;

import lombok.Data;

/**
 * 用户主页信息（对应 API.md §3.4.1）。
 */
@Data
public class UserProfileVO {

    private UserVO user;

    /** 发帖数 */
    private Long postCount;

    /** 粉丝数 */
    private Long followerCount;

    /** 关注数 */
    private Long followingCount;

    /** 当前用户是否已关注（未登录 false） */
    private Boolean isFollowed;
}
