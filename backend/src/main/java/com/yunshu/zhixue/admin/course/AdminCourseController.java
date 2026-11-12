package com.yunshu.zhixue.admin.course;

import com.yunshu.zhixue.admin.common.dto.SortOrderRequest;
import com.yunshu.zhixue.admin.common.dto.StatusResponse;
import com.yunshu.zhixue.admin.course.dto.CourseItem;
import com.yunshu.zhixue.admin.course.dto.CreateCourseRequest;
import com.yunshu.zhixue.admin.course.dto.UpdateCourseRequest;
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

/** 课程管理（04 §6.10.3，模块 course） */
@RestController
@RequestMapping("/api/v1/admin/courses")
@RequireModule("course")
public class AdminCourseController {

    private final AdminCourseService courseService;

    public AdminCourseController(AdminCourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ApiResponse<PageData<CourseItem>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) Long subjectId,
                                                  @RequestParam(required = false) Long stageId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(courseService.list(page, size, subjectId, stageId, keyword, status));
    }

    @PostMapping
    public ApiResponse<CourseItem> create(@Valid @RequestBody CreateCourseRequest request) {
        return ApiResponse.ok(courseService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseItem> detail(@PathVariable long id) {
        return ApiResponse.ok(courseService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CourseItem> update(@PathVariable long id,
                                          @Valid @RequestBody UpdateCourseRequest request) {
        return ApiResponse.ok(courseService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<StatusResponse> enable(@PathVariable long id) {
        courseService.enable(id);
        return ApiResponse.ok(new StatusResponse(id, "ENABLED"));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<StatusResponse> disable(@PathVariable long id) {
        courseService.disable(id);
        return ApiResponse.ok(new StatusResponse(id, "DISABLED"));
    }

    @PutMapping("/order")
    public ApiResponse<Void> reorder(@Valid @RequestBody SortOrderRequest request) {
        courseService.reorder(request);
        return ApiResponse.ok();
    }
}
