package com.dbd.service;

import com.dbd.vo.PostVO;

import java.util.List;

/**
 * 排行服务：热帖榜（对应 API.md §3.5.1）。
 */
public interface RankService {

    /** 热帖榜（ZSet，最多 20） */
    List<PostVO> hotPosts();

    /** 定时重算热帖榜：热度 = 浏览 + 点赞*2 + 楼层*4 */
    void rebuildHotPostRank();
}
