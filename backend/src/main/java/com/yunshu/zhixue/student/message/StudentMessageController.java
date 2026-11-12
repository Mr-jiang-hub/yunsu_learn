package com.yunshu.zhixue.student.message;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.message.dto.MessageItem;
import com.yunshu.zhixue.student.message.dto.ReadAllResponse;
import com.yunshu.zhixue.student.message.dto.UnreadCountResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端消息中心。
 *
 * <p>成员语义（谁的消息）完全由 access token 决定，路径里没有学员 ID，
 * 因此不存在越权读他人消息的可能；消息不存在统一返回 40039，
 * 不区分"没这条消息"和"这条消息不是发给你的"。</p>
 */
@RestController
@RequestMapping("/api/v1/c/messages")
public class StudentMessageController {

    private final StudentMessageService messageService;

    public StudentMessageController(StudentMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<PageData<MessageItem>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(messageService.list(CurrentUserContext.require(Audience.C).id(), pageQuery));
    }

    /** 静态路径必须排在 `/{messageId}` 之前声明，否则会被当作 ID 解析 */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(messageService.unreadCount(CurrentUserContext.require(Audience.C).id()));
    }

    @PostMapping("/{messageId}/read")
    public ApiResponse<MessageItem> read(@PathVariable long messageId) {
        return ApiResponse.ok(messageService.read(CurrentUserContext.require(Audience.C).id(), messageId));
    }

    @PostMapping("/read-all")
    public ApiResponse<ReadAllResponse> readAll() {
        return ApiResponse.ok(new ReadAllResponse(messageService.readAll(
                CurrentUserContext.require(Audience.C).id())));
    }

    @DeleteMapping("/{messageId}")
    public ApiResponse<Void> delete(@PathVariable long messageId) {
        messageService.delete(CurrentUserContext.require(Audience.C).id(), messageId);
        return ApiResponse.ok();
    }
}
