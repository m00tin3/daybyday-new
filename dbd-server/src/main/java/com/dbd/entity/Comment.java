package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 楼层实体，对应表 {@code comment}。
 */
@Data
@TableName("`comment`")
public class Comment {

    /** 状态：正常（唯一对前台可见的状态） */
    public static final int STATUS_NORMAL = 1;

    /**
     * 状态：已被作者删除（**软删除**）。
     * <p>用户在个人主页/帖子详情里删掉自己的回复时写这个值。对用户而言就是删了，
     * 前端不提供任何恢复入口；恢复只能管理员上服务器改 `status`。</p>
     */
    public static final int STATUS_DELETED = 0;

    /**
     * 该回复对前台是否可见。
     * <p>与 {@code Post.isVisible} 同理：可见性判断统一走这里，不要在业务代码里散写 {@code status == 1}。</p>
     * <p><b>注意</b>：楼层列表接口刻意**不**用它过滤顶层楼层 —— 已删楼层要留在列表里当占位，
     * 这样它的子回复（需求要求保留可见）才有父节点可挂。</p>
     */
    public static boolean isVisible(Integer status) {
        return status != null && status == STATUS_NORMAL;
    }

    /** 楼层ID（全局ID生成器） */
    @TableId
    private Long id;

    /** 帖子ID */
    private Long postId;

    /** 回复人ID */
    private Long userId;

    /** 楼层号（1 起，帖内递增，Redis INCR 生成；楼中楼固定 0，不占楼层号） */
    private Integer floorNo;

    /** 内容 */
    private String content;

    /** 图片URL列表（JSON数组字符串） */
    private String images;

    /** 楼中楼父楼层ID（null=直接回帖） */
    private Long parentId;

    /**
     * 被回复者ID（仅楼中楼有意义）。
     *
     * <p>两层结构下 {@link #parentId} 只指向顶层楼层，而用户可能是点某条**子回复**
     * 的「回复」——此时光看 parentId 只知道"回复了 1 楼"，不知道"回复的是 1 楼里那个人"。
     * 这一列就是被点的那条评论的作者，用于决定通知发给谁、以及前端显示「回复 @某某」。</p>
     *
     * <p>为 null 表示"回复的就是楼主层本身"，此时被回复者等于父楼层的作者。</p>
     */
    private Long replyToUserId;

    /** 点赞数（Redis 为准） */
    private Integer likeCount;

    /** 状态 1正常 0删除 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
