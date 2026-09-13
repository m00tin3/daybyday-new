package com.dbd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dbd.entity.Comment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CommentMapper extends BaseMapper<Comment> {

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
