package com.yunshu.zhixue.admin.question;

import com.yunshu.zhixue.admin.common.dto.StatusResponse;
import com.yunshu.zhixue.admin.question.dto.CreateQuestionRequest;
import com.yunshu.zhixue.admin.question.dto.ImportStartResponse;
import com.yunshu.zhixue.admin.question.dto.ImportTaskResponse;
import com.yunshu.zhixue.admin.question.dto.QuestionDetail;
import com.yunshu.zhixue.admin.question.dto.QuestionListItem;
import com.yunshu.zhixue.admin.question.dto.UpdateQuestionRequest;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.security.RequireModule;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 题库管理（04 §6.10.4，模块 question）。
 * 批量导入分三步：下载模板 → 上传启动异步任务 → 轮询任务结果。
 */
@RestController
@RequestMapping("/api/v1/admin/questions")
@RequireModule("question")
public class AdminQuestionController {

    private final AdminQuestionService questionService;
    private final QuestionImportService importService;

    public AdminQuestionController(AdminQuestionService questionService,
                                   QuestionImportService importService) {
        this.questionService = questionService;
        this.importService = importService;
    }

    @GetMapping
    public ApiResponse<PageData<QuestionListItem>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) Long subjectId,
                                                        @RequestParam(required = false) Long stageId,
                                                        @RequestParam(required = false) String type,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.ok(questionService.list(page, size, subjectId, stageId, type, keyword, status));
    }

    @PostMapping
    public ApiResponse<QuestionDetail> create(@Valid @RequestBody CreateQuestionRequest request) {
        return ApiResponse.ok(questionService.create(request));
    }

    /** 导入模板：xlsx 文件流，含表头、4 种题型示例行与填写说明页 */
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] body = importService.template();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("question-import-template.xlsx", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    /** 上传并启动导入：立刻返回 taskId，异步解析；任一行失败整批回滚 */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportStartResponse> importQuestions(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(importService.start(file, CurrentUserContext.require(Audience.ADMIN).id()));
    }

    @GetMapping("/import/{taskId}")
    public ApiResponse<ImportTaskResponse> importStatus(@PathVariable long taskId) {
        return ApiResponse.ok(importService.status(taskId, CurrentUserContext.require(Audience.ADMIN).id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(questionService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<QuestionDetail> update(@PathVariable long id,
                                              @Valid @RequestBody UpdateQuestionRequest request) {
        return ApiResponse.ok(questionService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<StatusResponse> enable(@PathVariable long id) {
        questionService.enable(id);
        return ApiResponse.ok(new StatusResponse(id, "ENABLED"));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<StatusResponse> disable(@PathVariable long id) {
        questionService.disable(id);
        return ApiResponse.ok(new StatusResponse(id, "DISABLED"));
    }
}
