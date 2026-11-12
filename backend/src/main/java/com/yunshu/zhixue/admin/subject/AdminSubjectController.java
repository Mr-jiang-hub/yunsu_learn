package com.yunshu.zhixue.admin.subject;

import com.yunshu.zhixue.admin.common.dto.SortOrderRequest;
import com.yunshu.zhixue.admin.common.dto.StatusResponse;
import com.yunshu.zhixue.admin.subject.dto.CreateSubjectRequest;
import com.yunshu.zhixue.admin.subject.dto.SubjectItem;
import com.yunshu.zhixue.admin.subject.dto.UpdateSubjectRequest;
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

/** 学科管理（04 §6.10.2，模块 subject） */
@RestController
@RequestMapping("/api/v1/admin/subjects")
@RequireModule("subject")
public class AdminSubjectController {

    private final AdminSubjectService subjectService;

    public AdminSubjectController(AdminSubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public ApiResponse<PageData<SubjectItem>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(subjectService.list(page, size, keyword, status));
    }

    @PostMapping
    public ApiResponse<SubjectItem> create(@Valid @RequestBody CreateSubjectRequest request) {
        return ApiResponse.ok(subjectService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<SubjectItem> detail(@PathVariable long id) {
        return ApiResponse.ok(subjectService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<SubjectItem> update(@PathVariable long id,
                                           @Valid @RequestBody UpdateSubjectRequest request) {
        return ApiResponse.ok(subjectService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<StatusResponse> enable(@PathVariable long id) {
        subjectService.enable(id);
        return ApiResponse.ok(new StatusResponse(id, "ENABLED"));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<StatusResponse> disable(@PathVariable long id) {
        subjectService.disable(id);
        return ApiResponse.ok(new StatusResponse(id, "DISABLED"));
    }

    @PutMapping("/order")
    public ApiResponse<Void> reorder(@Valid @RequestBody SortOrderRequest request) {
        subjectService.reorder(request);
        return ApiResponse.ok();
    }
}
