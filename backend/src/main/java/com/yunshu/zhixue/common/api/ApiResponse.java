package com.yunshu.zhixue.common.api;

import com.yunshu.zhixue.common.web.TraceIdFilter;

/**
 * 统一响应载体，契约见 docs/design/04.API接口设计.md 第 2 章。
 *
 * @param code    0 成功；非 0 为业务码
 * @param message 成功固定为 ok；失败为可展示的中文提示
 * @param data    业务数据；无数据时保留 null，不省略字段
 * @param traceId 请求追踪 ID
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data, TraceIdFilter.current());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(errorCode.getCode(), message, data, TraceIdFilter.current());
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage(), null);
    }
}
