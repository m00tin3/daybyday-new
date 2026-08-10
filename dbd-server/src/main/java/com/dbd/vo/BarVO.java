package com.dbd.vo;

import com.dbd.entity.Bar;
import lombok.Data;

/**
 * 吧视图对象（对应 API.md §2.2 BarVO）。
 */
@Data
public class BarVO {

    private Long id;
    private String name;
    private String description;
    private String cover;
    private Long memberCount;
    private Long postCount;
    /** 当前用户是否已关注（未登录 false） */
    private Boolean isFollowed;
    /** 今天是否已签到（未登录 false） */
    private Boolean signedToday;

    public static BarVO from(Bar bar) {
        BarVO vo = new BarVO();
        vo.setId(bar.getId());
        vo.setName(bar.getName());
        vo.setDescription(bar.getDescription());
        vo.setCover(bar.getCover());
        vo.setMemberCount(bar.getMemberCount() == null ? 0L : bar.getMemberCount().longValue());
        vo.setPostCount(bar.getPostCount() == null ? 0L : bar.getPostCount().longValue());
        return vo;
    }
}
