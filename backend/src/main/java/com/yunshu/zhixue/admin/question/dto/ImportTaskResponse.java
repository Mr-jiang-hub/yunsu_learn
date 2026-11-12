package com.yunshu.zhixue.admin.question.dto;

import java.util.List;

/**
 * 导入任务详情（04 §6.10.4）。
 * `FAILED` 时 `successRows=0`，整批回滚，错误行号是 Excel 里的真实行号（表头为第 1 行）。
 */
public record ImportTaskResponse(
        long taskId,
        String status,
        int totalRows,
        int successRows,
        int failedRows,
        List<RowError> errors,
        String createdAt,
        String finishedAt
) {

    public record RowError(int rowNo, String column, String message) {
    }
}
