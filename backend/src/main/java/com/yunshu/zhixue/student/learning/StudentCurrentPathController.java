package com.yunshu.zhixue.student.learning;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.learning.dto.CurrentPathResponse;
import com.yunshu.zhixue.student.learning.dto.UpdateCurrentPathRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设置/切换主路线（04 §6.3）。天然幂等：同 pathId 重复提交不报错，也不重置历史进度。
 */
@RestController
@RequestMapping("/api/v1/c/students/me")
public class StudentCurrentPathController {

    private final StudentLearningService learningService;

    public StudentCurrentPathController(StudentLearningService learningService) {
        this.learningService = learningService;
    }

    @PutMapping("/current-path")
    public ApiResponse<CurrentPathResponse> update(@Valid @RequestBody UpdateCurrentPathRequest request) {
        return ApiResponse.ok(learningService.updateCurrentPath(
                CurrentUserContext.require(Audience.C).id(), request.pathId()));
    }
}
