package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.dto.CommentDTO;
import com.dbd.dto.PostDTO;
import com.dbd.vo.CityStatVO;
import com.dbd.vo.CommentVO;
import com.dbd.vo.PostVO;

import java.util.List;
import java.util.Map;

/**
 * 帖子服务：列表 / 详情（缓存三件套）/ 发帖 / 点赞收藏 / 楼层（对应 API.md §3.2）。
 */
public interface PostService {

    /**
     * 帖子列表：无任何筛选条件时走首页缓存，带条件直查。
     *
     * @param barId   按吧筛选
     * @param userId  按作者筛选
     * @param city    按城市筛选（发帖时手动填写，替代已封存的 GEO 同城）
     * @param keyword 标题/正文关键词
     */
    PageResult<PostVO> page(Long barId, Long userId, String city, String keyword, Integer page, Integer size);

    /** 帖子详情：缓存三件套 + UV/浏览计数 */
    PostVO detail(Long id);

    /** 发帖：防重复提交 + 全局 ID，返回帖子ID */
    Long create(PostDTO dto);

    /** 点赞/取消（幂等切换），返回 { isLiked, likeCount } */
    Map<String, Object> like(Long postId);

    /** 收藏/取消（幂等切换），返回 { isFavorited, favoriteCount } */
    Map<String, Object> favorite(Long postId);

    /** 楼层列表（直接楼层，楼中楼后续迭代） */
    PageResult<CommentVO> comments(Long postId, Integer page, Integer size);

    /** 回帖：楼层号 INCR + 防重复提交，返回 { id, floorNo } */
    Map<String, Object> addComment(Long postId, CommentDTO dto);

    /**
     * 清除该帖的详情缓存、重建互斥锁与首页列表缓存。
     * <p>管理端隐藏/删除帖子后必须调用，否则前台在缓存 TTL 内仍能看到旧数据。</p>
     */
    void evictPostCache(Long postId);

    /**
     * 有帖子的城市列表（含数量，降序），用于"按城市浏览"页。
     * <p>结果带 Redis 缓存；发帖或管理端变更后会失效重建。</p>
     */
    List<CityStatVO> cities();
}
