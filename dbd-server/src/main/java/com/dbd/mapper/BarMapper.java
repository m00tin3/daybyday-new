package com.dbd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dbd.entity.Bar;
import org.apache.ibatis.annotations.Update;

public interface BarMapper extends BaseMapper<Bar> {

    /**
     * 把每个吧的 {@code post_count} 按**实际可见帖子数**重算（一条 SQL 纠正全表，不逐吧查）。
     *
     * <p><b>为什么需要它</b>：{@code bar.post_count} 没有任何写路径 —— 建吧时置 0 之后就再没人更新它，
     * 发帖/删帖都不碰。于是用户自建的吧永远显示「帖子 0」，而 {@code BarVO.from()} 会把这个值
     * 直接显示在吧主页（「关注 N · 帖子 N」）和管理后台的「帖子数」列上。</p>
     *
     * <p><b>为什么用定时重算而不是维护型计数</b>：{@code comment_count} 刚因为
     * 「读出来 +1 再写回」在并发下丢更新踩过坑。<b>维护型</b>计数天生容易漂移
     * （任何绕过写路径的操作都会让它错，且错了不会自愈）。这里挂在既有的
     * 5 分钟定时任务上按真实数据重算 —— 不会漂移，且能把历史脏数据一并纠正回来。
     * 代价是计数最多滞后 5 分钟，对「吧主页显示多少帖子」这种展示型数字完全可以接受。</p>
     *
     * <p>口径与前台列表一致：只统计 {@code status IN (1,2)}（正常/精华），
     * 隐藏(3)与已删除(0)不计入。</p>
     *
     * @return 受影响行数（= 吧总数）
     */
    @Update("UPDATE bar b SET b.post_count = ("
            + "SELECT COUNT(*) FROM post p WHERE p.bar_id = b.id AND p.status IN (1, 2))")
    int refreshPostCounts();
}
