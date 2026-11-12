package com.yunshu.zhixue.student.profile;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.auth.dto.SendSmsCodeResponse;
import com.yunshu.zhixue.student.profile.dto.CancelAccountRequest;
import com.yunshu.zhixue.student.profile.dto.ChangePasswordRequest;
import com.yunshu.zhixue.student.profile.dto.PhoneChangeRequest;
import com.yunshu.zhixue.student.profile.dto.PhoneChangeResponse;
import com.yunshu.zhixue.student.profile.dto.SelfSmsCodeRequest;
import com.yunshu.zhixue.student.profile.dto.StudentProfileResponse;
import com.yunshu.zhixue.student.profile.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端个人中心（04 §6.9）。
 * 只认 token 里的 studentId，不接受前端传参。
 */
@RestController
@RequestMapping("/api/v1/c/students")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;

    public StudentProfileController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    @GetMapping("/me")
    public ApiResponse<StudentProfileResponse> me() {
        return ApiResponse.ok(studentProfileService.profile(CurrentUserContext.require(Audience.C).id()));
    }

    /** 只允许昵称/头像；`status`、`phone`、`password` 等未声明字段由 Jackson 拒绝（10002） */
    @PatchMapping("/me")
    public ApiResponse<StudentProfileResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(studentProfileService.update(
                CurrentUserContext.require(Audience.C).id(), request));
    }

    /** 成功后全部 refresh token 撤销，前端需重新登录 */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        studentProfileService.changePassword(CurrentUserContext.require(Audience.C).id(), request);
        return ApiResponse.ok();
    }

    @PostMapping("/me/phone-change")
    public ApiResponse<PhoneChangeResponse> changePhone(@Valid @RequestBody PhoneChangeRequest request) {
        return ApiResponse.ok(studentProfileService.changePhone(
                CurrentUserContext.require(Audience.C).id(), request));
    }

    /** 给本人已绑定手机号发码：换绑验原号、注销验本人号，前端不必回填完整号码 */
    @PostMapping("/me/sms-codes")
    public ApiResponse<SendSmsCodeResponse> sendSelfCode(@Valid @RequestBody SelfSmsCodeRequest request) {
        return ApiResponse.ok(studentProfileService.sendSelfCode(
                CurrentUserContext.require(Audience.C).id(), request.purpose()));
    }

    @PostMapping("/me/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody CancelAccountRequest request) {
        studentProfileService.cancel(CurrentUserContext.require(Audience.C).id(), request);
        return ApiResponse.ok();
    }
}
