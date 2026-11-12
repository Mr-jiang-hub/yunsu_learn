package com.yunshu.zhixue.admin.path;

import com.yunshu.zhixue.admin.common.dto.SortOrderRequest;
import com.yunshu.zhixue.admin.common.dto.StatusResponse;
import com.yunshu.zhixue.admin.path.dto.CreateLearningPathRequest;
import com.yunshu.zhixue.admin.path.dto.CreateStageRequest;
import com.yunshu.zhixue.admin.path.dto.LearningPathDetail;
import com.yunshu.zhixue.admin.path.dto.LearningPathItem;
import com.yunshu.zhixue.admin.path.dto.StageResponse;
import com.yunshu.zhixue.admin.path.dto.UpdateLearningPathRequest;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.security.RequireModule;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 学习路线与阶段（04 §6.10.6 B-06，模块 learning_path） */
@RestController
@RequestMapping("/api/v1/admin/learning-paths")
@RequireModule("learning_path")
public class AdminLearningPathController {

    private final AdminLearningPathService learningPathService;

    public AdminLearningPathController(AdminLearningPathService learningPathService) {
        this.learningPathService = learningPathService;
    }

    @GetMapping
    public ApiResponse<PageData<LearningPathItem>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) Long subjectId,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.ok(learningPathService.list(page, size, subjectId, status));
    }

    @PostMapping
    public ApiResponse<LearningPathDetail> create(@Valid @RequestBody CreateLearningPathRequest request) {
        return ApiResponse.ok(learningPathService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<LearningPathDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(learningPathService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<LearningPathDetail> update(@PathVariable long id,
                                                  @Valid @RequestBody UpdateLearningPathRequest request) {
        return ApiResponse.ok(learningPathService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<StatusResponse> enable(@PathVariable long id) {
        learningPathService.enable(id);
        return ApiResponse.ok(new StatusResponse(id, "ENABLED"));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<StatusResponse> disable(@PathVariable long id) {
        learningPathService.disable(id);
        return ApiResponse.ok(new StatusResponse(id, "DISABLED"));
    }

    @PostMapping("/{id}/stages")
    public ApiResponse<StageResponse> createStage(@PathVariable long id,
                                                  @Valid @RequestBody CreateStageRequest request) {
        return ApiResponse.ok(learningPathService.createStage(id, request));
    }

    @PutMapping("/{id}/stages/order")
    public ApiResponse<Void> reorderStages(@PathVariable long id,
                                           @Valid @RequestBody SortOrderRequest request) {
        learningPathService.reorderStages(id, request);
        return ApiResponse.ok();
    }
}
