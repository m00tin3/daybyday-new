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

    /**
     * true = 该楼层已被作者删除（软删除），前端渲染成「该楼层已被删除」占位。
     *
     * <p>此时**只有** {@code id} / {@code floorNo} / {@code deleted} / {@code replies} /
     * {@code replyCount} 有值，其余字段（content / author / images / createdAt / likeCount /
     * replyToNickname）一律为 null —— 少清一个就等于把已删内容漏给前台。</p>
     */
    private Boolean deleted;

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

    /**
     * 已删除楼层的**占位** VO（前端渲染「该楼层已被删除」）。
     *
     * <p>刻意不复用 {@link #from(Comment, User)}：那个方法第一件事就是
     * {@code vo.setAuthor(UserVO.from(author))}，而 {@code UserVO.from} 会直接取
     * {@code user.getId()} —— 传 null 立刻 NPE。而且"复用 from 再逐字段清空"的写法
     * 只要漏清一个字段就把已删内容漏出去了，这里白名单式地只填该填的字段更安全。</p>
     *
     * <p>{@code replies} / {@code replyCount} 由调用方在组装时补上 —— 子回复按需求要保留可见，
     * 而前端是靠 {@code replyCount > 0} 决定渲不渲染那一块的，绝不能漏。</p>
     */
    public static CommentVO placeholder(Comment comment) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setFloorNo(comment.getFloorNo());
        vo.setDeleted(true);
        return vo;
    }
}
