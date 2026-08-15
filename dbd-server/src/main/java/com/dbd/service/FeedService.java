package com.dbd.service;

import com.dbd.vo.FeedResult;

/**
 * 关注 Feed 流服务（对应 API.md §3.5.2，阶段三）。
 */
public interface FeedService {

    /** 关注时间线滚动分页：ZSet 按 score 倒序 + lastId 游标 */
    FeedResult feed(Long lastId, Integer size);

    /** 写扩散：新帖推入所有关注该用户/该吧的粉丝时间线（发帖后调用） */
    void pushNewPost(Long postId, Long authorId, Long barId, double timestamp);
}
