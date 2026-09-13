package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.dto.CommentDTO;
import com.dbd.dto.NoticeCreateDTO;
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

    /**
     * 发布官方公告，返回公告对应的帖子ID。
     *
     * <p>公告本身是一条帖子（{@code type=1}、{@code bar_id=NULL}、{@code is_top=1}），
     * 复用帖子详情/回复/点赞/缓存/Feed 的全部链路。与 {@link #create} 的区别：
     * 不挂吧、不校验吧存在、恒定置顶、不填城市。</p>
     *
     * <p><b>权限</b>：本方法不自行判角色，调用方只应是 {@code AdminService}
     * （即 {@code /api/admin/**}，由 AdminInterceptor 强制 role=1）。</p>
     */
    Long publishNotice(NoticeCreateDTO dto);

    /** 点赞/取消（幂等切换），返回 { isLiked, likeCount } */
    Map<String, Object> like(Long postId);

    /** 收藏/取消（幂等切换），返回 { isFavorited, favoriteCount } */
    Map<String, Object> favorite(Long postId);

    /**
     * 楼层列表（只含顶层楼层；每层带第 1 页子回复与子回复总数）。
     * <p>{@code total} 是**楼层数**，不含子回复。</p>
     */
    PageResult<CommentVO> comments(Long postId, Integer page, Integer size);

    /**
     * 某一层楼下的子回复分页（楼中楼层内翻页）。
     *
     * @param floorId 顶层楼层ID
     */
    PageResult<CommentVO> floorReplies(Long postId, Long floorId, Integer page, Integer size);

    /** 回帖：楼层号 INCR + 防重复提交，返回 { id, floorNo } */
    Map<String, Object> addComment(Long postId, CommentDTO dto);

    /**
     * 用户删除自己的帖子（**软删除**：`post.status → 0`，仅本人可操作）。
     *
     * <p>只改帖子自己的状态，**不逐条改该帖楼层的状态** —— 否则回复者
     * 「TA 的回复」列表里的记录会一并消失（那里按 `comment.status = 1` 查）。
     * 帖子不可见后 `requirePost` 会抛 2002，整栋楼在效果上也就到不了了。</p>
     *
     * <p>已删除的帖子再次调用是**幂等**的（直接返回成功，不报错）。</p>
     */
    void deleteOwnPost(Long postId);

    /**
     * 用户删除自己的楼层或子回复（**软删除**：`comment.status → 0`，仅本人可操作）。
     *
     * <p>删顶层楼层时**其楼中楼保留可见**（需求要求），所以帖子回复数只减 1。
     * 已删除的回复再次调用是幂等的。</p>
     */
    void deleteOwnComment(Long postId, Long commentId);

    /**
     * 清除该帖的详情缓存、重建互斥锁与首页列表缓存。
     * <p>管理端隐藏/删除帖子后必须调用，否则前台在缓存 TTL 内仍能看到旧数据。</p>
     */
    void evictPostCache(Long postId);

    /**
     * 只清除首页列表缓存（各分页）。
     *
     * <p>列表缓存里存的是整份 PostVO，**包含作者的徽章**。因此除了发帖/删帖，
     * 用户抢到限量徽章时也必须让列表缓存失效——否则抢完回到首页，
     * 昵称旁的角标最长 60 秒（列表 TTL）后才出现。</p>
     */
    void evictHomeListCache();

    /**
     * 有帖子的城市列表（含数量，降序），用于"按城市浏览"页。
     * <p>结果带 Redis 缓存；发帖或管理端变更后会失效重建。</p>
     */
    List<CityStatVO> cities();
}
