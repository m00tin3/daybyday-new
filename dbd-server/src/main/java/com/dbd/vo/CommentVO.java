package com.dbd.vo;

import com.dbd.entity.Comment;
import com.dbd.entity.User;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 楼层视图对象（对应 API.md §2.4 CommentVO）。
 */
@Data
public class CommentVO {

    /** 楼层ID：序列化为字符串，避免 JS 大整数精度丢失（见 {@link UserVO#getId()} 说明） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属帖子ID：标识类字段 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long postId;

    private UserVO author;
    private Integer floorNo;
    private String content;
    private java.util.List<String> images;

    /** 楼中楼父楼层ID：标识类字段（可为 null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /**
     * 被回复者ID：楼中楼里"你点的那条评论是谁的"。
     * <p>与 {@link #parentId} 的区别：二层结构下 parentId 恒为顶层楼层，
     * 而用户可能点的是某条子回复——通知要发给这个人才对。为 null 表示回复楼主层本身。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyToUserId;

    /**
     * 被回复者的昵称，供前端直接渲染「回复 @某某」。
     * <p>为什么由后端给：被回复的那条子回复可能不在这几条预览里，
     * 前端无从反查它的作者。这里由楼层组装时顺带从批量查出的用户表里取。</p>
     */
    private String replyToNickname;

    /**
     * 楼中楼的子回复（仅顶层楼层带此字段；只挂前 {@code REPLY_PREVIEW} 条，
     * 其余靠 {@link #replyCount} 告知总数）。
     */
    private java.util.List<CommentVO> replies;

    /** 子回复总数（顶层楼层才有意义，子回复自身为 null） */
    private Long replyCount;

    /** 点赞数（计数类，保持数字） */
    private Long likeCount;

    private String createdAt;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static CommentVO from(Comment comment, User author) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setAuthor(UserVO.from(author));
        vo.setFloorNo(comment.getFloorNo());
        vo.setContent(comment.getContent());
        vo.setImages(PostVO.parseImages(comment.getImages()));
        vo.setParentId(comment.getParentId());
        vo.setReplyToUserId(comment.getReplyToUserId());
        vo.setLikeCount(comment.getLikeCount() == null ? 0L : comment.getLikeCount().longValue());
        vo.setCreatedAt(comment.getCreatedAt() == null ? null : comment.getCreatedAt().format(FMT));
        return vo;
    }
}
