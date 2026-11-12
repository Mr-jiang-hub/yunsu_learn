package com.yunshu.zhixue.admin.system;

import com.yunshu.zhixue.admin.system.dto.OrphanFileReport;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.AuthPrincipal;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.security.ModulePermissionChecker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B 端运维只读接口。
 *
 * <p>跨模块支撑接口：巡检要同时读 subject / course / student 三处 key，
 * 用类上固定的 {@code @RequireModule} 会误判归属，因此权限在方法内按 operation_log 模块判定。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/system")
public class AdminSystemController {

    private static final String OPS_MODULE = "operation_log";

    private final OrphanFileService orphanFileService;
    private final ModulePermissionChecker permissionChecker;

    public AdminSystemController(OrphanFileService orphanFileService,
                                 ModulePermissionChecker permissionChecker) {
        this.orphanFileService = orphanFileService;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 孤儿文件巡检（04-Q47 P0）。只读，返回候选清单供人工确认；本接口不删除任何对象。
     */
    @GetMapping("/orphan-files")
    public ApiResponse<OrphanFileReport> orphanFiles() {
        AuthPrincipal principal = CurrentUserContext.require(Audience.ADMIN);
        if (!permissionChecker.hasModule(principal.id(), OPS_MODULE)) {
            throw new BizException(ErrorCode.NO_MODULE_PERMISSION);
        }
        return ApiResponse.ok(orphanFileService.inspect());
    }
}
