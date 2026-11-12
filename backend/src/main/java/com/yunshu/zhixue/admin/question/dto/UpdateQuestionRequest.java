package com.yunshu.zhixue.admin.question.dto;

import com.yunshu.zhixue.admin.question.dto.CreateQuestionRequest.OptionInput;
import com.yunshu.zhixue.admin.question.dto.CreateQuestionRequest.ScoringPointInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 编辑题目（04 §6.10.4）：禁止 type/status；options 传则全量替换 */
public record UpdateQuestionRequest(
        @Size(max = 2000, message = "题干不能超过 2000 字符")
        String content,
        List<@Valid OptionInput> options,
        List<@Valid ScoringPointInput> scoringPoints,
        @Size(max = 2000, message = "解析不能超过 2000 字符")
        String analysis,
        @Size(max = 2000, message = "参考答案不能超过 2000 字符")
        String referenceAnswer,
        Long stageId
) {
}
