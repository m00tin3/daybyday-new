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

    /**
     * 回复数（顶层楼层 + 楼中楼）。
     * <p>口径**只按 `comment.status = 1`**，不管所属帖子是否被软删除 —— 否则数字会比
     * 「TA 的回复」列表的实际条数少（列表刻意保留"帖子已删"的记录，见
     * {@link com.dbd.vo.UserReplyVO#getPostDeleted()}）。</p>
     */
    private Long replyCount;

    /** 粉丝数 */
    private Long followerCount;

    /** 关注数 */
    private Long followingCount;

    /** 当前用户是否已关注（未登录 false） */
    private Boolean isFollowed;
}
