package com.dbd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dbd.entity.ActivityOrder;
import com.dbd.vo.BadgeVO;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 秒杀订单/徽章领取记录 Mapper。
 *
 * <p>{@link #selectBadgesByUserIds} 是徽章展示的唯一数据来源：帖子、楼层、
 * 用户主页都要显示作者的徽章，必须**一次批量查完**，不能在每个 VO 上单独查
 * （那会是典型的 N+1）。SQL 见 resources/mapper/ActivityOrderMapper.xml。</p>
 */
public interface ActivityOrderMapper extends BaseMapper<ActivityOrder> {

    /**
     * 批量查询多个用户已获得的限量徽章。
     *
     * @param userIds 用户ID集合（调用方需保证非空，空集合会生成非法 SQL）
     * @return 领取记录（含 userId，供调用方按用户分组）；无徽章的用户不会出现在结果里
     */
    List<BadgeVO> selectBadgesByUserIds(@Param("userIds") Collection<Long> userIds);

    /**
     * 查询单个用户已获得的限量徽章（徽章墙）。
     *
     * @param userId 用户ID
     * @return 按获得时间倒序的徽章列表
     */
    List<BadgeVO> selectBadgesByUserId(@Param("userId") Long userId);

    /** 物理删除某活动的全部领取记录（删除活动时级联清理） */
    int deleteByActivityId(@Param("activityId") Long activityId);
}
