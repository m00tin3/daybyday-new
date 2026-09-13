package com.dbd.vo;

import com.dbd.entity.Post;
import com.dbd.entity.User;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 帖子视图对象（对应 API.md §2.3 PostVO）。
 */
@Data
public class PostVO {

    /** 帖子ID：序列化为字符串，避免 JS 大整数精度丢失（见 {@link UserVO#getId()} 说明） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属吧ID：同样为标识类字段，需序列化为字符串；公告为 null */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long barId;

    /** 类型 0普通帖 1公告（前端据此渲染「公告」角标，优先级高于「顶」/「精」） */
    private Integer type;

    /** 吧名称；公告为 null，前端 v-if 会自动不渲染 */
    private String barName;
    private UserVO author;
    private String title;
    private String content;
    private List<String> images;

    /** 城市（发帖时手动填写，用于按城市浏览） */
    private String city;

    private Boolean isTop;
    private Integer status;

    /* 以下均为计数类字段，保持 JSON 数字（前端用于展示与分页，不参与 ID 比较） */
    private Long likeCount;
    private Long favoriteCount;
    private Long commentCount;
    private Long viewCount;
    private Long uvCount;

    private Boolean isLiked;
    private Boolean isFavorited;
    private String createdAt;

    /**
     * 距查询坐标的距离（米）。
     * <p>仅 GEO 同城接口返回；该功能已封存，此处保留字段以便恢复。</p>
     */
    private Double distance;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 列表行 → VO（content 为摘要，作者来自联表查询） */
    public static PostVO fromRow(PostRow row) {
        PostVO vo = new PostVO();
        vo.setId(row.getId());
        vo.setBarId(row.getBarId());
        vo.setType(row.getType());
        vo.setBarName(row.getBarName());
        UserVO author = new UserVO();
        author.setId(row.getUserId());
        author.setNickname(row.getAuthorNickname());
        author.setIcon(row.getAuthorIcon());
        vo.setAuthor(author);
        vo.setTitle(row.getTitle());
        vo.setContent(row.getContent());
        vo.setCity(row.getCity());
        vo.setIsTop(row.getIsTop() != null && row.getIsTop() == 1);
        vo.setStatus(row.getStatus());
        vo.setLikeCount(row.getLikeCount() == null ? 0L : row.getLikeCount().longValue());
        vo.setFavoriteCount(row.getFavoriteCount() == null ? 0L : row.getFavoriteCount().longValue());
        vo.setCommentCount(row.getCommentCount() == null ? 0L : row.getCommentCount().longValue());
        vo.setViewCount(row.getViewCount() == null ? 0L : row.getViewCount().longValue());
        vo.setCreatedAt(row.getCreatedAt() == null ? null : row.getCreatedAt().format(FMT));
        return vo;
    }

    /** 详情：entity + 作者 → VO（content 为全文） */
    public static PostVO from(Post post, User author, String barName) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setBarId(post.getBarId());
        vo.setType(post.getType());
        vo.setBarName(barName);
        vo.setAuthor(UserVO.from(author));
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setImages(parseImages(post.getImages()));
        vo.setCity(post.getCity());
        vo.setIsTop(post.getIsTop() != null && post.getIsTop() == 1);
        vo.setStatus(post.getStatus());
        vo.setLikeCount(post.getLikeCount() == null ? 0L : post.getLikeCount().longValue());
        vo.setFavoriteCount(post.getFavoriteCount() == null ? 0L : post.getFavoriteCount().longValue());
        vo.setCommentCount(post.getCommentCount() == null ? 0L : post.getCommentCount().longValue());
        vo.setViewCount(post.getViewCount() == null ? 0L : post.getViewCount().longValue());
        vo.setUvCount(post.getUvCount() == null ? 0L : post.getUvCount().longValue());
        vo.setCreatedAt(post.getCreatedAt() == null ? null : post.getCreatedAt().format(FMT));
        return vo;
    }

    /** DB 存的 JSON 数组字符串 → List */
    public static List<String> parseImages(String images) {
        if (images == null || images.isBlank() || "[]".equals(images)) {
            return Collections.emptyList();
        }
        String inner = images.substring(1, images.length() - 1).replace("\"", "");
        return Arrays.stream(inner.split(","))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
