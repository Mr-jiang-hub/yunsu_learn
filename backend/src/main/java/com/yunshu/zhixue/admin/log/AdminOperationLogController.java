package com.yunshu.zhixue.admin.log;

import com.yunshu.zhixue.admin.log.dto.OperationLogPage;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.RequireModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 操作日志（04 §6.10.10 B-10，模块 operation_log）：只读、游标分页、不提供导出 */
@RestController
@RequestMapping("/api/v1/admin/operation-logs")
@RequireModule("operation_log")
public class AdminOperationLogController {

    private final AdminOperationLogService logService;

    public AdminOperationLogController(AdminOperationLogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public ApiResponse<OperationLogPage> list(@RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) Long operatorId,
                                              @RequestParam(required = false) String moduleCode,
                                              @RequestParam(required = false) String action,
                                              @RequestParam(required = false) String targetType,
                                              @RequestParam(required = false) String targetId,
                                              @RequestParam(required = false) String result,
                                              @RequestParam(required = false) String startAt,
                                              @RequestParam(required = false) String endAt) {
        return ApiResponse.ok(logService.list(cursor, limit, operatorId, moduleCode, action,
                targetType, targetId, result, startAt, endAt));
    }
}
