package com.yunshu.zhixue.student.records;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.records.dto.CourseRecordItem;
import com.yunshu.zhixue.student.records.dto.InterviewCompareResponse;
import com.yunshu.zhixue.student.records.dto.MasteryTrendResponse;
import com.yunshu.zhixue.student.records.dto.PracticeRecordItem;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端学习与面试记录（04 §6.8）。
 *
 * <p>`/c/interview-sessions/compare` 是静态段路径，Spring MVC 会优先于
 * `/c/interview-sessions/{sessionId}` 匹配，前端按此路径调用。</p>
 */
@RestController
@RequestMapping("/api/v1/c")
public class StudentRecordsController {

    private final StudentRecordsService recordsService;

    public StudentRecordsController(StudentRecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @GetMapping("/records/courses")
    public ApiResponse<PageData<CourseRecordItem>> courses(
            @Valid PageQuery pageQuery,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long stageId) {
        return ApiResponse.ok(recordsService.courses(
                CurrentUserContext.require(Audience.C).id(), pageQuery, subjectId, stageId));
    }

    @GetMapping("/records/practices")
    public ApiResponse<PageData<PracticeRecordItem>> practices(
            @Valid PageQuery pageQuery,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long subjectId) {
        return ApiResponse.ok(recordsService.practices(
                CurrentUserContext.require(Audience.C).id(), pageQuery, type, status, subjectId));
    }

    @GetMapping("/records/mastery-trend")
    public ApiResponse<MasteryTrendResponse> masteryTrend(
            @RequestParam long stageId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(recordsService.masteryTrend(
                CurrentUserContext.require(Audience.C).id(), stageId, limit));
    }

    @GetMapping("/interview-sessions/compare")
    public ApiResponse<InterviewCompareResponse> compare(
            @RequestParam long baseSessionId,
            @RequestParam long targetSessionId) {
        return ApiResponse.ok(recordsService.compare(
                CurrentUserContext.require(Audience.C).id(), baseSessionId, targetSessionId));
    }
}
