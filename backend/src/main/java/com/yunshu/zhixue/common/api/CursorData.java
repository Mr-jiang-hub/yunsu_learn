package com.yunshu.zhixue.common.api;

import java.util.List;

/**
 * 游标分页响应，仅用于操作日志等追加型列表，契约见 04 文档 2.3。
 */
public record CursorData<T>(List<T> items, String nextCursor, boolean hasMore) {
}
