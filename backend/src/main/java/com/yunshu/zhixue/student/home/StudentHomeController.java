package com.yunshu.zhixue.student.home;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.home.dto.HomeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端首页（04 §6.2 C-02）：一次取齐路线、继续学习、练习、面试四块数据。
 * 只认 token 里的 studentId；区块级失败仍在 `meta.degradedSections` 中体现。
 */
@RestController
@RequestMapping("/api/v1/c/home")
public class StudentHomeController {

    private final StudentHomeService studentHomeService;

    public StudentHomeController(StudentHomeService studentHomeService) {
        this.studentHomeService = studentHomeService;
    }

    @GetMapping
    public ApiResponse<HomeResponse> home() {
        return ApiResponse.ok(studentHomeService.home(CurrentUserContext.require(Audience.C).id()));
    }
}
