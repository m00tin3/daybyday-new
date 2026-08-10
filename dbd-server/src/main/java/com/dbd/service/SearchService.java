package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.vo.PostVO;

import java.util.List;

/**
 * 搜索服务：热搜词 + 帖子搜索（对应 API.md §3.7）。
 */
public interface SearchService {

    /** 热搜词（ZSet 降序，最多 10） */
    List<String> hot();

    /** 搜索帖子：关键词命中 ZINCRBY 记录热搜 */
    PageResult<PostVO> search(String keyword, Integer page, Integer size);
}
