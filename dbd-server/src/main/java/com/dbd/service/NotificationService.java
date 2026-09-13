package com.dbd.service;

import com.dbd.common.PageResult;
import com.dbd.vo.NotificationVO;

/**
 * 消息通知服务：把「回复我的帖子 / 回复我的楼层 / 赞了我的帖子」统一成一条消息。
 */
public interface NotificationService {

    /**
     * 创建一条通知。
     *
     * <p>内部会做两件事，调用方不必重复判断：</p>
     * <ul>
     *   <li><b>不给自己发</b>：{@code toUserId} 为空或与 {@code fromUserId} 相同则直接返回</li>
     *   <li><b>点赞去重</b>：同一人对同一帖的点赞只留一条（否则反复赞/取消会刷屏）</li>
     * </ul>
     *
     * <p>本方法是**非关键路径**：通知写失败不应该让回复/点赞整体失败，
     * 因此内部吞掉异常只记日志。</p>
     *
     * @param toUserId   接收者
     * @param type       见 {@link com.dbd.entity.Notification#TYPE_REPLY_POST} 等常量
     * @param fromUserId 触发者
     * @param postId     相关帖子
     * @param commentId  相关楼层（点赞类通知传 null）
     */
    void notify(Long toUserId, Integer type, Long fromUserId, Long postId, Long commentId);

    /** 我的通知列表（分页，时间倒序） */
    PageResult<NotificationVO> list(Integer page, Integer size);

    /** 我的未读数（未登录返回 0 —— 它只用于导航红点，没必要为它弹错误提示） */
    long unreadCount();

    /** 把当前用户的未读通知全部标为已读 */
    void readAll();
}
