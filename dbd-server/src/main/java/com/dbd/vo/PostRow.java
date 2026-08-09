package com.dbd.vo;

import lombok.Data;

/**
 * 帖子列表联表查询行（join bar/user 补全吧名与作者信息），SQL 见 PostMapper.xml。
 */
@Data
public class PostRow {

    private Long id;
    private Long barId;
    private Long userId;
    private String title;
    private String content;
    private Integer isTop;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Integer viewCount;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime lastCommentTime;
    /** 吧名称（join bar） */
    private String barName;
    /** 作者昵称（join user） */
    private String authorNickname;
    /** 作者头像（join user） */
    private String authorIcon;
}
