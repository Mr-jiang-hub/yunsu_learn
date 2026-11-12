package com.yunshu.zhixue.admin.log.dto;

import java.util.List;

/** 操作日志游标分页响应（04 §6.10.10）：不返回 total */
public record OperationLogPage(
        List<OperationLogItem> items,
        String nextCursor,
        boolean hasMore
) {
}
