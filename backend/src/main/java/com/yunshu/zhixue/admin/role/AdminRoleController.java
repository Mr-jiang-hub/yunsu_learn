package com.yunshu.zhixue.admin.role;

import com.yunshu.zhixue.admin.role.dto.RoleDetail;
import com.yunshu.zhixue.admin.role.dto.RoleItem;
import com.yunshu.zhixue.admin.role.dto.UpdateRoleModulesRequest;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.RequireModule;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色权限（04 §6.10.9 B-09，模块 role）：固定三角色，只读 + 模块全量覆盖 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequireModule("role")
public class AdminRoleController {

    private final AdminRoleService roleService;

    public AdminRoleController(AdminRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ApiResponse<RolesResponse> list() {
        return ApiResponse.ok(new RolesResponse(roleService.list()));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(roleService.detail(id));
    }

    @PutMapping("/{id}/modules")
    public ApiResponse<RoleDetail> updateModules(@PathVariable long id,
                                                 @Valid @RequestBody UpdateRoleModulesRequest request) {
        return ApiResponse.ok(roleService.updateModules(id, request));
    }

    /** 04 文档的 data 形如 {items:[...]} */
    public record RolesResponse(List<RoleItem> items) {
    }
}
