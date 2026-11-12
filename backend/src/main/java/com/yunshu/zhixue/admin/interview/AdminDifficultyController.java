package com.yunshu.zhixue.admin.interview;

import com.yunshu.zhixue.admin.common.dto.StatusResponse;
import com.yunshu.zhixue.admin.interview.dto.DifficultyItem;
import com.yunshu.zhixue.admin.interview.dto.UpdateDifficultyRequest;
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

import java.util.List;
import java.util.Map;

/**
 * 面试难度档位（04 §6.10.5）。全局固定 3 档，`code` 不可改，删除语义统一为停用。
 */
@RestController
@RequestMapping("/api/v1/admin/interview-difficulties")
@RequireModule("interview_settings")
public class AdminDifficultyController {

    private final AdminInterviewService interviewService;

    public AdminDifficultyController(AdminInterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping
    public ApiResponse<Map<String, List<DifficultyItem>>> list() {
        return ApiResponse.ok(Map.of("items", interviewService.listDifficulties()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DifficultyItem> update(@PathVariable long id,
                                              @Valid @RequestBody UpdateDifficultyRequest request) {
        return ApiResponse.ok(interviewService.updateDifficulty(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<StatusResponse> enable(@PathVariable long id) {
        interviewService.enableDifficulty(id);
        return ApiResponse.ok(new StatusResponse(id, "ENABLED"));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<StatusResponse> disable(@PathVariable long id) {
        interviewService.disableDifficulty(id);
        return ApiResponse.ok(new StatusResponse(id, "DISABLED"));
    }
}
