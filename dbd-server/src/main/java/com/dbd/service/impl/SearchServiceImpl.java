package com.dbd.service.impl;

import com.dbd.common.BusinessException;
import com.dbd.common.PageResult;
import com.dbd.service.PostService;
import com.dbd.service.SearchService;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.vo.PostVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 搜索服务实现：热搜词 ZSet（score=搜索次数，命中即 +1）。
 */
@Service
public class SearchServiceImpl implements SearchService {

    private final PostService postService;
    private final StringRedisTemplate stringRedisTemplate;

    public SearchServiceImpl(PostService postService, StringRedisTemplate stringRedisTemplate) {
        this.postService = postService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<String> hot() {
        return stringRedisTemplate.opsForZSet()
                .reverseRange(RedisKeyConstants.SEARCH_HOT, 0, 9).stream().toList();
    }

    @Override
    public PageResult<PostVO> search(String keyword, Integer page, Integer size) {
        if (keyword == null || keyword.isBlank()) {
            throw BusinessException.param("搜索关键词不能为空");
        }
        // 搜索即记录热搜（ZINCRBY 加 1）
        stringRedisTemplate.opsForZSet().incrementScore(RedisKeyConstants.SEARCH_HOT, keyword, 1);
        return postService.page(null, null, keyword, page, size);
    }
}
