package com.yunshu.zhixue.admin.dashboard;

import com.yunshu.zhixue.admin.dashboard.dto.DashboardResponse;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.RequireModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 数据看板（04 §6.10.11 B-11，模块 dashboard）：只读，不提供导出 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequireModule("dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> load(@RequestParam(required = false) String range) {
        return ApiResponse.ok(dashboardService.load(range));
    }
}
