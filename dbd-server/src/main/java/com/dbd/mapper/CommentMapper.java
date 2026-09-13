package com.dbd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbd.entity.Comment;
import com.dbd.vo.UserReplyRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 某人的回复列表（个人主页「TA 的回复」），联表补全所属帖子标题与可见性，SQL 见 CommentMapper.xml。
     *
     * <p><b>刻意用 LEFT JOIN 且不过滤帖子状态</b>：帖子被楼主软删后，回复者主页里这条记录
     * 仍要显示（点进去才提示已删除）。任何"顺手加上 status IN (1,2)"的改动都会让需求失效。</p>
     */
    IPage<UserReplyRow> selectRepliesByUser(Page<Comment> page, @Param("userId") Long userId);

    /**
     * 帖子当前最大**顶层**楼层号（无楼层返回 0）。
     * 用于回帖时对齐 Redis 楼层计数器：种子数据已占用 1..N 层时，新楼层从 N+1 继续。
     *
     * <p>{@code parent_id IS NULL} 这个条件不能少：楼中楼不再占用楼层号（floor_no 固定 0），
     * 但**存量**子回复是带楼层号的历史数据，不排除掉的话会把计数器顶到虚高的位置，
     * 导致下一个顶层楼层号直接跳号。</p>
     */
    @Select("SELECT COALESCE(MAX(floor_no), 0) FROM `comment` "
            + "WHERE post_id = #{postId} AND parent_id IS NULL")
    Long selectMaxFloor(@Param("postId") Long postId);
}
