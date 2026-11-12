package com.yunshu.zhixue.common.api;

/**
 * 字段级校验错误，code 取值见 openapi/common.yaml FieldError：
 * REQUIRED / PATTERN / LENGTH / RANGE / ENUM / FORMAT / INVALID。
 */
public record FieldErrorItem(String field, String code, String message) {
}
