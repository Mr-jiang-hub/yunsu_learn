package com.yunshu.zhixue.common.api;

import java.util.List;

/**
 * 偏移分页响应，契约见 docs/design/04.API接口设计.md 2.3。
 */
public record PageData<T>(List<T> items, int page, int size, long total, boolean hasNext) {

    public static <T> PageData<T> of(List<T> items, int page, int size, long total) {
        return new PageData<>(items, page, size, total, (long) page * size < total);
    }
}
