package com.dbd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbd.entity.Notification;
import com.dbd.vo.NotificationRow;
import org.apache.ibatis.annotations.Param;

/**
 * 消息通知 Mapper。
 *
 * <p>其余查询（未读数、标记已读、点赞去重）用继承来的 {@code BaseMapper} 即可，
 * 只有列表需要联表补全"触发者昵称/头像、帖子标题、回复内容摘要"，故单独走 XML。</p>
 */
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 某人的通知列表（联表补全展示所需字段），按时间倒序。
     *
     * <p>三个联表一律 {@code LEFT JOIN}：帖子可能已被删除、楼层可能已随帖子清理，
     * 但通知本身是历史记录，不能因为关联对象没了就把整条通知吞掉
     * （前端对空标题显示「帖子已删除」）。</p>
     */
    IPage<NotificationRow> selectNotificationPage(Page<Notification> page,
                                                  @Param("userId") Long userId);
}
