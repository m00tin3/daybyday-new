package com.dbd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dbd.entity.Comment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 帖子当前最大楼层号（无楼层返回 0）。
     * 用于回帖时对齐 Redis 楼层计数器：种子数据已占用 1..N 层时，新楼层从 N+1 继续。
     */
    @Select("SELECT COALESCE(MAX(floor_no), 0) FROM `comment` WHERE post_id = #{postId}")
    Long selectMaxFloor(@Param("postId") Long postId);
}
