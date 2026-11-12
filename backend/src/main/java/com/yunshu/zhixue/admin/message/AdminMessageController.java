package com.yunshu.zhixue.admin.message;

import com.yunshu.zhixue.admin.message.dto.CreateMessageRequest;
import com.yunshu.zhixue.admin.message.dto.MessageItem;
import com.yunshu.zhixue.admin.message.dto.SendMessageRequest;
import com.yunshu.zhixue.admin.message.dto.UpdateMessageRequest;
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
 * 后台消息推送（模块 message）。
 *
 * <p>发送/撤回是独立的 POST 动作接口而非 PATCH status：两者都要写审计、
 * 且发送还要落收件行，语义上不是"改个字段"。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/messages")
@RequireModule("message")
public class AdminMessageController {

    private final AdminMessageService messageService;

    public AdminMessageController(AdminMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<PageData<MessageItem>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(messageService.list(page, size, keyword, status));
    }

    @PostMapping
    public ApiResponse<MessageItem> create(@Valid @RequestBody CreateMessageRequest request) {
        return ApiResponse.ok(messageService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<MessageItem> detail(@PathVariable long id) {
        return ApiResponse.ok(messageService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<MessageItem> update(@PathVariable long id,
                                           @Valid @RequestBody UpdateMessageRequest request) {
        return ApiResponse.ok(messageService.update(id, request));
    }

    /** 发送：body 可省略；定向消息必须带 studentIds */
    @PostMapping("/{id}/send")
    public ApiResponse<MessageItem> send(@PathVariable long id,
                                         @RequestBody(required = false) SendMessageRequest request) {
        return ApiResponse.ok(messageService.send(id, request == null ? null : request.studentIds()));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<MessageItem> revoke(@PathVariable long id) {
        return ApiResponse.ok(messageService.revoke(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        messageService.delete(id);
        return ApiResponse.ok();
    }
}
