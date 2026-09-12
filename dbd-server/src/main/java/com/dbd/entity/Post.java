package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 帖子实体，对应表 {@code post}。
 */
@Data
@TableName("post")
public class Post {

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 1;

    /** 状态：已删除（历史值；当前管理端删除为物理删除，仅存量数据可能为该值） */
    public static final int STATUS_DELETED = 0;

    /** 状态：精华 */
    public static final int STATUS_FEATURED = 2;

    /**
     * 状态：隐藏（管理端操作，前台全链路不可见，仅 /admin 可见并可恢复）。
     * <p>可见性判断统一走 {@link #isVisible(Integer)}，不要在业务代码里散写 status == 0。</p>
     */
    public static final int STATUS_HIDDEN = 3;

    /**
     * 帖子对前台是否可见：仅 1正常 / 2精华 可见。
     * <p>前台列表 SQL 使用 {@code WHERE status IN (1,2)}；
     * 而详情缓存、Feed 组装、热帖榜、收藏列表等走内存过滤的路径，
     * 必须统一调用本方法，否则隐藏帖会从这些入口泄露出去。</p>
     */
    public static boolean isVisible(Integer status) {
        return status != null && (status == STATUS_NORMAL || status == STATUS_FEATURED);
    }

    /** 帖子ID（全局ID生成器） */
    @TableId
    private Long id;

    /** 所属吧ID */
    private Long barId;

    /** 发帖人ID */
    private Long userId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 图片URL列表（JSON数组字符串） */
    private String images;

    /** 经度（同城 GEO，可空） */
    private Double longitude;

    /** 纬度（同城 GEO，可空） */
    private Double latitude;

    /** 状态 1正常 0删除 2精华 3隐藏 */
    private Integer status;

    /** 是否置顶 0否 1是 */
    private Integer isTop;

    /** 点赞数（Redis 为准，异步落库） */
    private Integer likeCount;

    /** 收藏数（Redis 为准，异步落库） */
    private Integer favoriteCount;

    /** 楼层数（Redis 为准，异步落库） */
    private Integer commentCount;

    /** 浏览量（Redis 为准，异步落库） */
    private Integer viewCount;

    /** 独立访客数（HyperLogLog，定时落库） */
    private Integer uvCount;

    /** 热度分（ZSet 排行用，定时重算） */
    private BigDecimal score;

    /** 最后回复时间（列表排序用） */
    private LocalDateTime lastCommentTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
