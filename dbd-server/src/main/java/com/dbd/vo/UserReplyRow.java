package com.dbd.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「某人的回复」联表查询行（LEFT JOIN post 补全所属帖子信息），SQL 见 CommentMapper.xml。
 */
@Data
public class UserReplyRow {

    private Long id;
    private Long postId;
    private String content;
    private Integer floorNo;

    /** 非空 = 楼中楼（挂在这条顶层楼层下）；为空 = 顶层楼层本身 */
    private Long parentId;

    private LocalDateTime createdAt;

    /** 所属帖子标题（LEFT JOIN post）。帖子被物理删除时为 null */
    private String postTitle;

    /**
     * 所属帖子状态：1正常 / 2精华 / 0用户删除 / 3管理员隐藏。
     * <p>帖子被物理删除时整行为 null —— 与"软删除"一样要按不可见处理。</p>
     */
    private Integer postStatus;
}
