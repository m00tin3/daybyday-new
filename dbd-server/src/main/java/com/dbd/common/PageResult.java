package com.dbd.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页返回结构（对应 API.md §1.4）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> list;

    /** 总条数 */
    private Long total;

    private Integer page;

    private Integer size;

    public static <T> PageResult<T> of(List<T> list, Long total, Integer page, Integer size) {
        return new PageResult<>(list, total, page, size);
    }
}
