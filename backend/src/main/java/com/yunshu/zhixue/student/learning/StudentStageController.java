package com.yunshu.zhixue.student.learning;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.learning.dto.CourseProgressResponse;
import com.yunshu.zhixue.student.learning.dto.StageDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端阶段详情与课程进度（04 §6.4）。
 *
 * <p>开始/完成课程均为幂等：唯一键 `(student_id, course_id)` 去重，重复调用返回首次时间。</p>
 */
@RestController
@RequestMapping("/api/v1/c")
public class StudentStageController {

    private final StudentLearningService learningService;

    public StudentStageController(StudentLearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/stages/{stageId}")
    public ApiResponse<StageDetailResponse> stage(@PathVariable long stageId) {
        return ApiResponse.ok(learningService.stageDetail(stageId, CurrentUserContext.require(Audience.C).id()));
    }

    @PostMapping("/courses/{courseId}/start")
    public ApiResponse<CourseProgressResponse> start(@PathVariable long courseId) {
        return ApiResponse.ok(learningService.startCourse(courseId, CurrentUserContext.require(Audience.C).id()));
    }

    @PostMapping("/courses/{courseId}/complete")
    public ApiResponse<CourseProgressResponse> complete(@PathVariable long courseId) {
        return ApiResponse.ok(learningService.completeCourse(courseId, CurrentUserContext.require(Audience.C).id()));
    }
}
