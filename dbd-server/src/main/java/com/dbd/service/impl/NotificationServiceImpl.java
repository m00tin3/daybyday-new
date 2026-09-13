package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbd.common.BusinessException;
import com.dbd.common.PageResult;
import com.dbd.entity.Notification;
import com.dbd.mapper.NotificationMapper;
import com.dbd.service.BadgeService;
import com.dbd.service.NotificationService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.UserContext;
import com.dbd.vo.NotificationRow;
import com.dbd.vo.NotificationVO;
import com.dbd.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息通知实现。
 *
 * <p><b>未读数为什么不用 Redis 计数器</b>：点赞数刚出过"Redis 写、DB 不写，
 * 两边不一致导致前台恒显示 0"的事故。未读数是低频读取（导航上一个红点），
 * 走 DB 的 {@code COUNT(*)} 配上 {@code idx_user_read} 完全够用，
 * 没必要为了它再维护一处需要双写的状态。</p>
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final RedisIdWorker redisIdWorker;
    private final BadgeService badgeService;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   RedisIdWorker redisIdWorker,
                                   BadgeService badgeService) {
        this.notificationMapper = notificationMapper;
        this.redisIdWorker = redisIdWorker;
        this.badgeService = badgeService;
    }

    @Override
    public void notify(Long toUserId, Integer type, Long fromUserId, Long postId, Long commentId) {
        if (toUserId == null || fromUserId == null || type == null || postId == null) {
            return;
        }
        // 自己回复自己、自己赞自己都不该收到通知
        if (toUserId.equals(fromUserId)) {
            return;
        }
        try {
            // 点赞去重：反复点赞/取消不应该刷屏。同一人对同一帖只保留一条点赞通知，
            // 且 unlike 既不发新通知也不撤回旧通知（历史记录不回滚）。
            if (type == Notification.TYPE_LIKE_POST && likeNotificationExists(toUserId, fromUserId, postId)) {
                return;
            }
            Notification n = new Notification();
            n.setId(redisIdWorker.nextId("notification"));
            n.setUserId(toUserId);
            n.setType(type);
            n.setFromUserId(fromUserId);
            n.setPostId(postId);
            n.setCommentId(commentId);
            n.setIsRead(0);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
            log.info("通知已创建 to={}, type={}, from={}, postId={}", toUserId, type, fromUserId, postId);
        } catch (Exception e) {
            // 通知是非关键路径：写失败不该让用户的回复/点赞整体失败。
            // 这里吞掉异常只记日志，是有意的取舍（回复已经落库，回滚它才是更糟的结果）。
            log.error("通知写入失败（已忽略）to={}, type={}, from={}, postId={}",
                    toUserId, type, fromUserId, postId, e);
        }
    }

    private boolean likeNotificationExists(Long toUserId, Long fromUserId, Long postId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, toUserId)
                .eq(Notification::getType, Notification.TYPE_LIKE_POST)
                .eq(Notification::getFromUserId, fromUserId)
                .eq(Notification::getPostId, postId));
        return count != null && count > 0;
    }

    @Override
    public PageResult<NotificationVO> list(Integer page, Integer size) {
        Long userId = requireLogin();
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 50);
        IPage<NotificationRow> rows = notificationMapper.selectNotificationPage(new Page<>(p, s), userId);
        List<NotificationVO> list = rows.getRecords().stream().map(NotificationVO::fromRow).toList();
        // 触发者昵称旁的限量徽章角标：一次批量查，避免逐个用户回表
        badgeService.fillAuthors(list.stream().map(NotificationVO::getFromUser).toList());
        return PageResult.of(list, rows.getTotal(), p, s);
    }

    @Override
    public long unreadCount() {
        Long userId = UserContext.get();
        if (userId == null) {
            // 未登录返回 0 而不是抛 2003：这个接口只服务于导航上的红点，
            // 会在每次路由变化时被调用，抛错会导致 token 过期后满屏错误提示
            return 0L;
        }
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return count == null ? 0L : count;
    }

    @Override
    public void readAll() {
        Long userId = requireLogin();
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    private Long requireLogin() {
        Long userId = UserContext.get();
        if (userId == null) {
            throw BusinessException.forbidden("请先登录");
        }
        return userId;
    }
}
