package com.yunshu.zhixue.common.exception;

import com.yunshu.zhixue.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 业务异常：业务失败默认 HTTP 200 + 业务码（04-Q22 已确认）。
 * 认证、授权、限流、协议类错误按 ErrorCode 中登记的 HTTP 状态返回。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object data;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BizException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}
