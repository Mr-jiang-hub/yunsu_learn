package com.yunshu.zhixue.student.interview;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.interview.dto.AudioTranscribeResponse;
import com.yunshu.zhixue.student.interview.dto.CreateSessionRequest;
import com.yunshu.zhixue.student.interview.dto.CreateSessionResponse;
import com.yunshu.zhixue.student.interview.dto.FinishRequest;
import com.yunshu.zhixue.student.interview.dto.FinishResponse;
import com.yunshu.zhixue.student.interview.dto.InterviewOptionsResponse;
import com.yunshu.zhixue.student.interview.dto.InterviewQuotaView;
import com.yunshu.zhixue.student.interview.dto.InterviewReportResponse;
import com.yunshu.zhixue.student.interview.dto.InterviewSessionDetailResponse;
import com.yunshu.zhixue.student.interview.dto.InterviewSessionListItem;
import com.yunshu.zhixue.student.interview.dto.SubmitAnswerRequest;
import com.yunshu.zhixue.student.interview.dto.SubmitAnswerResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * C 端 AI 模拟面试（04 §6.7）。
 *
 * <p>`Idempotency-Key` 是创建接口的必填头；语音接口必须是 multipart，音频只在内存中过一遍。</p>
 */
@RestController
@RequestMapping("/api/v1/c")
public class StudentInterviewController {

    private final StudentInterviewService interviewService;

    public StudentInterviewController(StudentInterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/interview-options")
    public ApiResponse<InterviewOptionsResponse> options() {
        return ApiResponse.ok(interviewService.options(CurrentUserContext.require(Audience.C).id()));
    }

    @GetMapping("/interview-quota")
    public ApiResponse<InterviewQuotaView> quota() {
        return ApiResponse.ok(interviewService.quota(CurrentUserContext.require(Audience.C).id()));
    }

    @PostMapping("/interview-sessions")
    public ApiResponse<CreateSessionResponse> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(interviewService.create(
                CurrentUserContext.require(Audience.C).id(), request, idempotencyKey));
    }

    @GetMapping("/interview-sessions")
    public ApiResponse<PageData<InterviewSessionListItem>> list(
            @Valid PageQuery pageQuery,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.ok(interviewService.list(
                CurrentUserContext.require(Audience.C).id(), pageQuery, subjectId, status, startDate, endDate));
    }

    @GetMapping("/interview-sessions/{sessionId}")
    public ApiResponse<InterviewSessionDetailResponse> detail(@PathVariable long sessionId) {
        return ApiResponse.ok(interviewService.detail(
                sessionId, CurrentUserContext.require(Audience.C).id()));
    }

    @PostMapping("/interview-sessions/{sessionId}/answers")
    public ApiResponse<SubmitAnswerResponse> submitAnswer(@PathVariable long sessionId,
                                                          @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.ok(interviewService.submitAnswer(
                sessionId, CurrentUserContext.require(Audience.C).id(), request));
    }

    @PostMapping(value = "/interview-sessions/{sessionId}/answers/audio",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AudioTranscribeResponse> transcribe(@PathVariable long sessionId,
                                                           @RequestParam Integer round,
                                                           @RequestPart("audio") MultipartFile audio) {
        return ApiResponse.ok(interviewService.transcribe(
                sessionId, CurrentUserContext.require(Audience.C).id(), round, audio));
    }

    @PostMapping("/interview-sessions/{sessionId}/finish")
    public ApiResponse<FinishResponse> finish(@PathVariable long sessionId,
                                              @RequestBody(required = false) FinishRequest request) {
        FinishRequest safe = request == null ? new FinishRequest(null) : request;
        return ApiResponse.ok(interviewService.finish(
                sessionId, CurrentUserContext.require(Audience.C).id(), safe.confirmed()));
    }

    @GetMapping("/interview-sessions/{sessionId}/report")
    public ApiResponse<InterviewReportResponse> report(@PathVariable long sessionId) {
        return ApiResponse.ok(interviewService.report(
                sessionId, CurrentUserContext.require(Audience.C).id()));
    }
}
