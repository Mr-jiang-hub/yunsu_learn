package com.yunshu.zhixue.student.practice;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.practice.dto.JudgeView;
import com.yunshu.zhixue.student.practice.dto.PracticeDetailResponse;
import com.yunshu.zhixue.student.practice.dto.PracticeResultResponse;
import com.yunshu.zhixue.student.practice.dto.PracticeStartResponse;
import com.yunshu.zhixue.student.practice.dto.PracticeSubmitResponse;
import com.yunshu.zhixue.student.practice.dto.SelfAssessmentRequest;
import com.yunshu.zhixue.student.practice.dto.SubmitAnswerRequest;
import com.yunshu.zhixue.student.practice.dto.SubmitAnswerResponse;
import com.yunshu.zhixue.student.practice.dto.WrongRetryRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端阶段练习与错题重练（04 §6.5）。
 */
@RestController
@RequestMapping("/api/v1/c")
public class StudentPracticeController {

    private final StudentPracticeService practiceService;

    public StudentPracticeController(StudentPracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @PostMapping("/stages/{stageId}/practices")
    public ApiResponse<PracticeStartResponse> start(@PathVariable long stageId) {
        return ApiResponse.ok(practiceService.startStagePractice(
                stageId, CurrentUserContext.require(Audience.C).id()));
    }

    @PostMapping("/practices/wrong-questions")
    public ApiResponse<PracticeStartResponse> startWrongRetry(
            @RequestBody(required = false) WrongRetryRequest request) {
        WrongRetryRequest safe = request == null ? new WrongRetryRequest(null, null) : request;
        return ApiResponse.ok(practiceService.startWrongRetry(
                CurrentUserContext.require(Audience.C).id(), safe));
    }

    @GetMapping("/practices/{practiceId}")
    public ApiResponse<PracticeDetailResponse> detail(@PathVariable long practiceId) {
        return ApiResponse.ok(practiceService.detail(
                practiceId, CurrentUserContext.require(Audience.C).id()));
    }

    @PostMapping("/practices/{practiceId}/answers")
    public ApiResponse<SubmitAnswerResponse> submitAnswer(@PathVariable long practiceId,
                                                         @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.ok(practiceService.submitAnswer(
                practiceId, CurrentUserContext.require(Audience.C).id(), request));
    }

    @PostMapping("/practices/{practiceId}/answers/{questionId}/self-assessment")
    public ApiResponse<JudgeView> selfAssess(@PathVariable long practiceId,
                                             @PathVariable long questionId,
                                             @Valid @RequestBody SelfAssessmentRequest request) {
        return ApiResponse.ok(practiceService.selfAssess(
                practiceId, questionId, CurrentUserContext.require(Audience.C).id(), request));
    }

    @PostMapping("/practices/{practiceId}/submit")
    public ApiResponse<PracticeSubmitResponse> submit(@PathVariable long practiceId) {
        return ApiResponse.ok(practiceService.submit(
                practiceId, CurrentUserContext.require(Audience.C).id()));
    }

    @GetMapping("/practices/{practiceId}/result")
    public ApiResponse<PracticeResultResponse> result(@PathVariable long practiceId) {
        return ApiResponse.ok(practiceService.result(
                practiceId, CurrentUserContext.require(Audience.C).id()));
    }
}
