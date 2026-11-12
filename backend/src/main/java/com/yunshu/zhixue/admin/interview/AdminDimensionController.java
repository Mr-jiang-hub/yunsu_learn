package com.yunshu.zhixue.admin.interview;

import com.yunshu.zhixue.admin.common.dto.StatusResponse;
import com.yunshu.zhixue.admin.interview.dto.CreateDimensionRequest;
import com.yunshu.zhixue.admin.interview.dto.DimensionItem;
import com.yunshu.zhixue.admin.interview.dto.ReorderDimensionsRequest;
import com.yunshu.zhixue.admin.interview.dto.UpdateDimensionRequest;
import com.yunshu.zhixue.common.api.ApiResponse;
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

import java.util.List;
import java.util.Map;

/**
 * 学科能力维度（04 §6.10.5）。维度不跨学科移动；`subjectId` 建后不可改，删除语义为停用。
 */
@RestController
@RequestMapping("/api/v1/admin/dimensions")
@RequireModule("interview_settings")
public class AdminDimensionController {

    private final AdminInterviewService interviewService;

    public AdminDimensionController(AdminInterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping
    public ApiResponse<Map<String, List<DimensionItem>>> list(@RequestParam long subjectId) {
        return ApiResponse.ok(Map.of("items", interviewService.listDimensions(subjectId)));
    }

    @PostMapping
    public ApiResponse<DimensionItem> create(@Valid @RequestBody CreateDimensionRequest request) {
        return ApiResponse.ok(interviewService.createDimension(request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DimensionItem> update(@PathVariable long id,
                                             @Valid @RequestBody UpdateDimensionRequest request) {
        return ApiResponse.ok(interviewService.updateDimension(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<StatusResponse> enable(@PathVariable long id) {
        interviewService.enableDimension(id);
        return ApiResponse.ok(new StatusResponse(id, "ENABLED"));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<StatusResponse> disable(@PathVariable long id) {
        interviewService.disableDimension(id);
        return ApiResponse.ok(new StatusResponse(id, "DISABLED"));
    }

    @PutMapping("/order")
    public ApiResponse<Void> reorder(@Valid @RequestBody ReorderDimensionsRequest request) {
        interviewService.reorderDimensions(request);
        return ApiResponse.ok(null);
    }
}
