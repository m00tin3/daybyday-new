package com.dbd.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布 / 编辑限量徽章活动请求体（对应 POST /api/admin/activity）。
 *
 * <p>徽章称号取自前端预设的 4 个常用称号，也允许管理员手工输入新称号；
 * 因此这里只做长度与字符校验，不做枚举白名单，否则无法扩展。</p>
 */
@Data
public class ActivityCreateDTO {

    /** 限量徽章称号，如「凤川祥」。全局唯一（一个称号同时只有一个活动） */
    @NotBlank(message = "徽章称号不能为空")
    @Size(min = 1, max = 32, message = "徽章称号长度需在 1-32 字之间")
    private String badgeName;

    /** 发放数量（限量库存），1 ~ 100000 */
    @NotNull(message = "发放数量不能为空")
    @Min(value = 1, message = "发放数量至少为 1")
    @Max(value = 100000, message = "发放数量过大")
    private Integer stock;

    /** 开始时间，格式 yyyy-MM-dd HH:mm:ss */
    @NotBlank(message = "开始时间不能为空")
    private String beginTime;

    /** 结束时间，格式 yyyy-MM-dd HH:mm:ss */
    @NotBlank(message = "结束时间不能为空")
    private String endTime;

    /** 奖励说明（可选，留空则由后端按称号与数量自动生成） */
    @Size(max = 255, message = "奖励说明不能超过 255 字")
    private String awardDesc;

    /** 关联吧ID（可空；徽章一般是平台级荣誉，不挂靠某个吧） */
    private Long barId;
}
