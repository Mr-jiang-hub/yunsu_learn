package com.yunshu.zhixue.admin.student;

import com.yunshu.zhixue.admin.common.dto.StatusResponse;
import com.yunshu.zhixue.admin.student.dto.CancelStudentRequest;
import com.yunshu.zhixue.admin.student.dto.ResetPasswordResponse;
import com.yunshu.zhixue.admin.student.dto.StudentDetail;
import com.yunshu.zhixue.admin.student.dto.StudentListItem;
import com.yunshu.zhixue.admin.student.dto.UpdateCurrentPathRequest;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.security.RequireModule;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 学员管理（04 §6.10.7 B-07，模块 student） */
@RestController
@RequestMapping("/api/v1/admin/students")
@RequireModule("student")
public class AdminStudentController {

    private final AdminStudentService studentService;

    public AdminStudentController(AdminStudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    public ApiResponse<PageData<StudentListItem>> list(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(required = false) String phone,
                                                       @RequestParam(required = false) String nickname,
                                                       @RequestParam(required = false) Long pathId,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String startDate,
                                                       @RequestParam(required = false) String endDate) {
        return ApiResponse.ok(studentService.list(page, size, phone, nickname, pathId, status, startDate, endDate));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(studentService.detail(id));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable long id) {
        return ApiResponse.ok(studentService.resetPassword(id));
    }

    @PutMapping("/{id}/current-path")
    public ApiResponse<Map<String, Object>> updateCurrentPath(@PathVariable long id,
                                                              @Valid @RequestBody UpdateCurrentPathRequest request) {
        return ApiResponse.ok(studentService.updateCurrentPath(id, request));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<StatusResponse> disable(@PathVariable long id) {
        studentService.disable(id);
        return ApiResponse.ok(new StatusResponse(id, "DISABLED"));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<StatusResponse> enable(@PathVariable long id) {
        studentService.enable(id);
        return ApiResponse.ok(new StatusResponse(id, "ACTIVE"));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable long id,
                                                   @RequestBody(required = false) CancelStudentRequest request) {
        return ApiResponse.ok(studentService.cancel(id, request));
    }
}
