package com.dbd.common;

import lombok.Getter;

/**
 * 业务异常：服务层/控制层主动抛出，由 {@link GlobalExceptionHandler} 统一转成 Result 返回。
 * <p>错误码规范见 API.md §1.5：2001 参数错误 / 2002 资源不存在 / 2003 无权限 /
 * 3001 验证码 / 3002 频繁操作 / 4001-4004 秒杀相关。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final Integer code;

    public BusinessException(String msg) {
        this(0, msg);
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }

    /** 2001 参数错误 */
    public static BusinessException param(String msg) {
        return new BusinessException(2001, msg);
    }

    /** 2002 资源不存在 */
    public static BusinessException notFound(String msg) {
        return new BusinessException(2002, msg);
    }

    /** 2003 无权限 */
    public static BusinessException forbidden(String msg) {
        return new BusinessException(2003, msg);
    }

    /** 3001 验证码错误/过期 */
    public static BusinessException codeError(String msg) {
        return new BusinessException(3001, msg);
    }

    /** 3002 操作过于频繁（防重复提交） */
    public static BusinessException tooFast(String msg) {
        return new BusinessException(3002, msg);
    }

    /** 秒杀业务失败（4001 未开始/已结束、4002 售罄、4003 重复抢） */
    public static BusinessException seckill(Integer code, String msg) {
        return new BusinessException(code, msg);
    }
}
