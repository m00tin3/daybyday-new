package com.dbd.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 用户已获得徽章视图对象（徽章墙 / 作者昵称旁角标）。
 *
 * <p>由 {@code activity_order JOIN activity} 得出，不单独建表：
 * 领取记录已经在 activity_order 里，徽章称号是 activity 的属性，
 * 冗余一张 user_badge 表只会带来双写不一致的风险。</p>
 */
@Data
public class BadgeVO {

    /** 活动ID：标识类字段，序列化为字符串避免 JS 大整数精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /**
     * 所属用户ID。
     * <p>仅批量查询（帖子/楼层列表一次性取多个作者的徽章）时返回，
     * 用于把结果按用户分组；单个用户的徽章墙接口会把它清空，不必重复下发。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 徽章称号，如「凤川祥」 */
    private String badgeName;

    /** 活动标题 */
    private String title;

    /** 奖励说明 */
    private String awardDesc;

    /** 获得时间 yyyy-MM-dd HH:mm:ss */
    private String awardedAt;
}
