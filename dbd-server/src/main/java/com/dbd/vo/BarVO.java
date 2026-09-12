package com.dbd.vo;

import com.dbd.entity.Bar;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 吧视图对象（对应 API.md §2.2 BarVO）。
 */
@Data
public class BarVO {

    /**
     * 吧ID：序列化为字符串。
     * <p>管理后台新建的吧由 Redis 全局 ID 生成器分配 18-19 位 ID，超过 JS 安全整数范围；
     * 若按数字下发，发帖页下拉框选中的 ID 会被浏览器四舍五入，
     * 提交后后端查不到该吧并返回"吧不存在"。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String description;

    private String cover;

    /** 关注人数（计数类，保持 JSON 数字） */
    private Long memberCount;

    /** 帖子数（计数类，保持 JSON 数字） */
    private Long postCount;

    /** 状态 1正常 0隐藏（仅管理后台展示与筛选用，前台只返回正常吧） */
    private Integer status;

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
        vo.setStatus(bar.getStatus() == null ? 1 : bar.getStatus());
        return vo;
    }
}
