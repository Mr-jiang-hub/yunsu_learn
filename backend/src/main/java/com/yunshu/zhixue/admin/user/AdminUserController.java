package com.yunshu.zhixue.admin.user;

import com.yunshu.zhixue.admin.common.dto.StatusResponse;
import com.yunshu.zhixue.admin.student.dto.ResetPasswordResponse;
import com.yunshu.zhixue.admin.user.dto.AdminUserDetail;
import com.yunshu.zhixue.admin.user.dto.AdminUserItem;
import com.yunshu.zhixue.admin.user.dto.CreateAdminUserRequest;
import com.yunshu.zhixue.admin.user.dto.UpdateAdminUserRequest;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.security.RequireModule;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台账号（04 §6.10.8 B-08，模块 admin_user） */
@RestController
@RequestMapping("/api/v1/admin/admin-users")
@RequireModule("admin_user")
public class AdminUserController {

    private final AdminUserService userService;

    public AdminUserController(AdminUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageData<AdminUserItem>> list(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(required = false) Long roleId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userService.list(page, size, roleId, status, keyword));
    }

    @PostMapping
    public ApiResponse<AdminUserDetail> create(@Valid @RequestBody CreateAdminUserRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(userService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminUserDetail> update(@PathVariable long id,
                                               @Valid @RequestBody UpdateAdminUserRequest request) {
        return ApiResponse.ok(userService.update(id, request));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<StatusResponse> disable(@PathVariable long id) {
        userService.disable(id);
        return ApiResponse.ok(new StatusResponse(id, "DISABLED"));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<StatusResponse> enable(@PathVariable long id) {
        userService.enable(id);
        return ApiResponse.ok(new StatusResponse(id, "ACTIVE"));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable long id) {
        return ApiResponse.ok(userService.resetPassword(id));
    }
}
