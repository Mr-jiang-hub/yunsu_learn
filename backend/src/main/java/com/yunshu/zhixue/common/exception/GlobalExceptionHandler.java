package com.yunshu.zhixue.common.exception;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.api.FieldErrorItem;
import com.yunshu.zhixue.common.api.ValidationErrorData;
import com.yunshu.zhixue.common.web.TraceIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局异常处理：所有异常统一转换为 ApiResponse，业务代码不各自拼响应。
 * 系统异常只返回通用提示 + traceId，不暴露堆栈、SQL、类名。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBizException(BizException ex) {
        if (ex.getHttpStatus().is5xxServerError()) {
            log.error("业务异常（系统级）code={}, traceId={}", ex.getErrorCode().getCode(), TraceIdFilter.current(), ex);
        } else {
            log.debug("业务失败 code={}, message={}, traceId={}",
                    ex.getErrorCode().getCode(), ex.getMessage(), TraceIdFilter.current());
        }
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.fail(ex.getErrorCode(), ex.getMessage(), ex.getData()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorItem(fieldError.getField(), mapFieldCode(fieldError.getCode()),
                    defaultMessage(fieldError.getDefaultMessage())));
        }
        return validationFailure(errors);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleBindException(BindException ex) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorItem(fieldError.getField(), mapFieldCode(fieldError.getCode()),
                    defaultMessage(fieldError.getDefaultMessage())));
        }
        return validationFailure(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String field = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
            String annotation = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            errors.add(new FieldErrorItem(field, mapFieldCode(annotation), defaultMessage(violation.getMessage())));
        }
        return validationFailure(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorItem> errors = new ArrayList<>();
        ex.getAllValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> errors.add(new FieldErrorItem(
                    parameterName == null ? "" : parameterName,
                    "INVALID",
                    defaultMessage(error.getDefaultMessage()))));
        });
        return validationFailure(errors);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleMissingOrMismatch(Exception ex) {
        String field = ex instanceof MissingServletRequestParameterException missing ? missing.getParameterName()
                : ((MethodArgumentTypeMismatchException) ex).getName();
        return validationFailure(List.of(new FieldErrorItem(field, "REQUIRED", "参数缺失或类型不正确")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("请求体无法解析, traceId={}", TraceIdFilter.current());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ErrorCode.BODY_UNREADABLE));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.fail(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.fail(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(ErrorCode.NO_MODULE_PERMISSION));
    }

    /**
     * 访问未定义的路径：Spring 默认抛 NoResourceFoundException，落进兜底分支会变成
     * 500「系统内部错误」，把客户端的拼写错误伪装成服务端故障。这里显式转成 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        log.debug("接口不存在: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未捕获异常, traceId={}", TraceIdFilter.current(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR));
    }

    private static ResponseEntity<ApiResponse<ValidationErrorData>> validationFailure(List<FieldErrorItem> errors) {
        ValidationErrorData data = new ValidationErrorData(errors);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(), data));
    }

    private static String defaultMessage(String message) {
        return message == null || message.isBlank() ? "参数不合法" : message;
    }

    private static String mapFieldCode(String validationCode) {
        if (validationCode == null) {
            return "INVALID";
        }
        return switch (validationCode) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Size", "Length" -> "LENGTH";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
                    "Negative", "NegativeOrZero", "Past", "PastOrPresent", "Future", "FutureOrPresent" -> "RANGE";
            case "Email" -> "FORMAT";
            case "Pattern" -> "PATTERN";
            default -> "INVALID";
        };
    }
}
