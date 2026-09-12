package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.dto.CommentDTO;
import com.dbd.dto.PostDTO;
import com.dbd.vo.CommentVO;
import com.dbd.vo.PostVO;

import java.util.Map;

/**
 * 帖子服务：列表 / 详情（缓存三件套）/ 发帖 / 点赞收藏 / 楼层（对应 API.md §3.2）。
 */
public interface PostService {

    /** 帖子列表：无筛选走首页缓存，带筛选直查 */
    PageResult<PostVO> page(Long barId, Long userId, String keyword, Integer page, Integer size);

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
}
