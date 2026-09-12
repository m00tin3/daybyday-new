package com.dbd.vo;

import lombok.Data;

/**
 * 城市统计项（"按城市浏览"页面的城市列表）。
 */
@Data
public class CityStatVO {

    /** 城市名 */
    private String city;

    /** 该城市下前台可见的帖子数 */
    private Long postCount;
}
