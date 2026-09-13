package com.dbd.vo;

import com.dbd.entity.Post;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 「某人的回复」视图对象（个人主页「TA 的回复」Tab，对应 API.md §3.4.7）。
 *
 * <p>与 {@link CommentVO} 分开：这里的关注点是"我回过哪些帖子"，需要带**所属帖子**的标题与可见性，
 * 而 CommentVO 关注的是楼层在帖子内的展现（replies/floorNo 等），形状完全不同。</p>
 */
@Data
public class UserReplyVO {

    /** 回复ID（楼层或子回复），标识类字段序列化为字符串 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属帖子ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long postId;

    private String content;

    /** 顶层楼层号；楼中楼为 0（哨兵，不占楼层号） */
    private Integer floorNo;

    /** true = 楼中楼（回在某条楼层下）；false = 顶层楼层。前端据此显示「楼中楼」标记 */
    private Boolean nested;

    private String createdAt;

    /**
     * 所属帖子标题。
     * <p>帖子被楼主的**软删除**时行仍在，标题照常查得到 —— 所以标题本身不能用来判断"帖子还在"。
     * 帖子被管理员**物理删除**时才为 null。</p>
     */
    private String postTitle;

    /**
     * 所属帖子是否已不可见（用户软删 / 管理员隐藏 / 被物理删除）。
     * <p>前端据此决定"点击是跳转还是弹提示"——只给标题不给这个标记的话，
     * 前端会以为帖子还在、跳过去撞 404。</p>
     */
    private Boolean postDeleted;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static UserReplyVO fromRow(UserReplyRow row) {
        UserReplyVO vo = new UserReplyVO();
        vo.setId(row.getId());
        vo.setPostId(row.getPostId());
        vo.setContent(row.getContent());
        vo.setFloorNo(row.getFloorNo());
        vo.setNested(row.getParentId() != null);
        vo.setCreatedAt(row.getCreatedAt() == null ? null : row.getCreatedAt().format(FMT));
        vo.setPostTitle(row.getPostTitle());
        // postStatus 为 null 表示 post 行已被物理删除 → 同样按"已删除"处理
        vo.setPostDeleted(!Post.isVisible(row.getPostStatus()));
        return vo;
    }
}
