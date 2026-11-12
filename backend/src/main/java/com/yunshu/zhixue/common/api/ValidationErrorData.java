package com.yunshu.zhixue.common.api;

import java.util.List;

/**
 * 字段级校验失败的 data 结构，契约见 04 文档 2.4。
 */
public record ValidationErrorData(List<FieldErrorItem> errors, boolean truncated) {

    public ValidationErrorData(List<FieldErrorItem> errors) {
        this(errors, false);
    }
}
