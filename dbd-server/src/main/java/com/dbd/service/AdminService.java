package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.dto.BarCreateDTO;
import com.dbd.vo.BarVO;
import com.dbd.vo.PostVO;

/**
 * 管理服务（仅 role=1 可调用，鉴权见 {@link com.dbd.interceptor.AdminInterceptor}）。
 *
 * <p>删除语义与前台不同，明确区分：</p>
 * <ul>
 *   <li><b>隐藏</b>：只改状态（帖子 3 / 吧 0），数据保留，前台全链路不可见，可恢复</li>
 *   <li><b>删除</b>：物理 DELETE，不可恢复；同时清理关联数据与 Redis 残留 key</li>
 * </ul>
 */
public interface AdminService {

    /* ---------- 帖子 ---------- */

    /** 帖子管理列表：包含隐藏/精华等全部状态（前台列表只返回 1/2） */
    PageResult<PostVO> postList(String keyword, Integer status, Integer page, Integer size);

    /** 隐藏帖子：status → 3，前台详情/列表/排行/Feed 均不可见 */
    void hidePost(Long postId);

    /** 恢复帖子：status → 1（原精华标记不保留） */
    void restorePost(Long postId);

    /** 物理删除帖子：连同楼层、点赞、收藏一并清理，并清除 Redis 缓存与计数 key */
    void deletePost(Long postId);

    /* ---------- 吧 ---------- */

    /** 吧管理列表：包含已隐藏（status=0）的吧 */
    PageResult<BarVO> barList(String keyword, Integer status, Integer page, Integer size);

    /** 创建贴吧（名称全局唯一），返回新吧 ID */
    Long createBar(BarCreateDTO dto);

    /** 隐藏吧：status → 0，前台不可见且不进热吧榜 */
    void hideBar(Long barId);

    /** 恢复吧：status → 1 */
    void restoreBar(Long barId);

    /** 物理删除吧：连同其下全部帖子（含各帖子关联数据）一并清理 */
    void deleteBar(Long barId);
}
