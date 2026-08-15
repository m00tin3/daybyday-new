package com.dbd.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 关注 Feed 流滚动分页结果（对应 API.md §3.5.2）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedResult {

    /** 当前页帖子 */
    private List<PostVO> list;

    /** 滚动分页游标（本页最后一条的 score，即发帖时间戳毫秒） */
    private Long lastId;

    /** 是否还有更多 */
    private Boolean hasMore;
}
