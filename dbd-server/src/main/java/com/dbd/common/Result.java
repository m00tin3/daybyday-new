package com.dbd.common;

import lombok.Data;

/**
 * 统一响应包装。
 * <p>约定：{@code code = 1} 成功；{@code code != 1} 失败（msg 携带展示给用户的信息）。
 * 与前端 src/utils/request.js 的响应拦截器约定一致。</p>
 */
@Data
public class Result<T> {

    /** 状态码：1 成功，其他为失败 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    /** 成功（无数据） */
    public static <T> Result<T> ok() {
        return ok(null, "ok");
    }

    /** 成功（携带数据） */
    public static <T> Result<T> ok(T data) {
        return ok(data, "ok");
    }

    /** 成功（携带数据与提示） */
    public static <T> Result<T> ok(T data, String msg) {
        Result<T> r = new Result<>();
        r.setCode(1);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    /** 失败（code=0 通用失败） */
    public static <T> Result<T> fail(String msg) {
        return fail(0, msg);
    }

    /** 失败（自定义错误码，见 API.md §1.5 错误码表） */
    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
