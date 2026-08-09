package com.dbd.vo;

import com.dbd.entity.Comment;
import com.dbd.entity.User;
import lombok.Data;

import java.time.format.DateTimeFormatter;

/**
 * 楼层视图对象（对应 API.md §2.4 CommentVO）。
 */
@Data
public class CommentVO {

    private Long id;
    private Long postId;
    private UserVO author;
    private Integer floorNo;
    private String content;
    private java.util.List<String> images;
    private Long parentId;
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
        vo.setLikeCount(comment.getLikeCount() == null ? 0L : comment.getLikeCount().longValue());
        vo.setCreatedAt(comment.getCreatedAt() == null ? null : comment.getCreatedAt().format(FMT));
        return vo;
    }
}
