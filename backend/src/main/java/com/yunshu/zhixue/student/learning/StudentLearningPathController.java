package com.yunshu.zhixue.student.learning;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.learning.dto.LearningPathListResponse;
import com.yunshu.zhixue.student.learning.dto.PathDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端学习路线（04 §6.3）。学员身份只取 token，不接受前端传 studentId。
 */
@RestController
@RequestMapping("/api/v1/c/learning-paths")
public class StudentLearningPathController {

    private final StudentLearningService learningService;

    public StudentLearningPathController(StudentLearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping
    public ApiResponse<LearningPathListResponse> list() {
        return ApiResponse.ok(learningService.listPaths(CurrentUserContext.require(Audience.C).id()));
    }

    @GetMapping("/{pathId}")
    public ApiResponse<PathDetailResponse> detail(@PathVariable long pathId) {
        return ApiResponse.ok(learningService.pathDetail(pathId, CurrentUserContext.require(Audience.C).id()));
    }
}
