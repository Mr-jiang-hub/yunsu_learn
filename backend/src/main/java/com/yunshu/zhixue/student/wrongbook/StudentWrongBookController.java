package com.yunshu.zhixue.student.wrongbook;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.student.wrongbook.dto.WrongQuestionDetail;
import com.yunshu.zhixue.student.wrongbook.dto.WrongQuestionPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端错题本（04 §6.6）：只读，无手动移除接口。
 */
@RestController
@RequestMapping("/api/v1/c/wrong-questions")
public class StudentWrongBookController {

    private final StudentWrongBookService wrongBookService;

    public StudentWrongBookController(StudentWrongBookService wrongBookService) {
        this.wrongBookService = wrongBookService;
    }

    @GetMapping
    public ApiResponse<WrongQuestionPage> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) Long subjectId,
                                              @RequestParam(required = false) Long stageId) {
        return ApiResponse.ok(wrongBookService.list(
                CurrentUserContext.require(Audience.C).id(), page, size, subjectId, stageId));
    }

    @GetMapping("/{questionId}")
    public ApiResponse<WrongQuestionDetail> detail(@PathVariable long questionId) {
        return ApiResponse.ok(wrongBookService.detail(
                CurrentUserContext.require(Audience.C).id(), questionId));
    }
}
