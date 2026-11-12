package com.yunshu.zhixue.admin.path;

import com.yunshu.zhixue.admin.common.dto.StatusResponse;
import com.yunshu.zhixue.admin.path.dto.StageContents;
import com.yunshu.zhixue.admin.path.dto.StageResponse;
import com.yunshu.zhixue.admin.path.dto.UpdateStageRequest;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.RequireModule;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阶段单资源接口（04 §6.10.6）。阶段在 URL 上不体现路线，因此单独成控制器；
 * UI 的"删除阶段"映射到 disable，不物理删除。
 */
@RestController
@RequestMapping("/api/v1/admin/stages")
@RequireModule("learning_path")
public class AdminStageController {

    private final AdminLearningPathService learningPathService;

    public AdminStageController(AdminLearningPathService learningPathService) {
        this.learningPathService = learningPathService;
    }

    @PatchMapping("/{stageId}")
    public ApiResponse<StageResponse> update(@PathVariable long stageId,
                                             @Valid @RequestBody UpdateStageRequest request) {
        return ApiResponse.ok(learningPathService.updateStage(stageId, request));
    }

    @PostMapping("/{stageId}/enable")
    public ApiResponse<StatusResponse> enable(@PathVariable long stageId) {
        learningPathService.enableStage(stageId);
        return ApiResponse.ok(new StatusResponse(stageId, "ENABLED"));
    }

    @PostMapping("/{stageId}/disable")
    public ApiResponse<StatusResponse> disable(@PathVariable long stageId) {
        learningPathService.disableStage(stageId);
        return ApiResponse.ok(new StatusResponse(stageId, "DISABLED"));
    }

    @GetMapping("/{stageId}/contents")
    public ApiResponse<StageContents> contents(@PathVariable long stageId) {
        return ApiResponse.ok(learningPathService.contents(stageId));
    }
}
