package com.yunshu.zhixue.admin.notice;

import com.yunshu.zhixue.admin.notice.dto.CreateNoticeRequest;
import com.yunshu.zhixue.admin.notice.dto.NoticeItem;
import com.yunshu.zhixue.admin.notice.dto.UpdateNoticeRequest;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.security.RequireModule;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告管理（模块 notice）。
 *
 * <p>发布与下线用 POST 动作接口而不是 PATCH status，
 * 与学科/课程的 enable/disable 风格保持一致：状态变更需要独立的权限与审计语义。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/notices")
@RequireModule("notice")
public class AdminNoticeController {

    private final AdminNoticeService noticeService;

    public AdminNoticeController(AdminNoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public ApiResponse<PageData<NoticeItem>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.ok(noticeService.list(page, size, keyword, status));
    }

    @PostMapping
    public ApiResponse<NoticeItem> create(@Valid @RequestBody CreateNoticeRequest request) {
        return ApiResponse.ok(noticeService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoticeItem> detail(@PathVariable long id) {
        return ApiResponse.ok(noticeService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<NoticeItem> update(@PathVariable long id,
                                          @Valid @RequestBody UpdateNoticeRequest request) {
        return ApiResponse.ok(noticeService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<NoticeItem> publish(@PathVariable long id) {
        return ApiResponse.ok(noticeService.publish(id));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<NoticeItem> offline(@PathVariable long id) {
        return ApiResponse.ok(noticeService.offline(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        noticeService.delete(id);
        return ApiResponse.ok();
    }
}
