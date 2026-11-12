package com.yunshu.zhixue.admin.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 新增题目（04 §6.10.4） */
public record CreateQuestionRequest(
        @NotBlank(message = "题型不能为空")
        String type,
        @NotNull(message = "学科不能为空")
        Long subjectId,
        @NotNull(message = "阶段不能为空")
        Long stageId,
        @NotBlank(message = "题干不能为空")
        @Size(max = 2000, message = "题干不能超过 2000 字符")
        String content,
        List<@Valid OptionInput> options,
        List<@Valid ScoringPointInput> scoringPoints,
        @Size(max = 2000, message = "解析不能超过 2000 字符")
        String analysis,
        @Size(max = 2000, message = "参考答案不能超过 2000 字符")
        String referenceAnswer
) {

    /** optionId 为空表示新增；isCorrect 标记正确答案 */
    public record OptionInput(
            String optionKey,
            @NotBlank(message = "选项内容不能为空")
            @Size(max = 1000, message = "选项内容不能超过 1000 字符")
            String content,
            @NotNull(message = "必须标记选项是否为正确答案")
            Boolean isCorrect,
            Integer sortNo
    ) {
    }

    public record ScoringPointInput(
            @NotBlank(message = "评分点内容不能为空")
            @Size(max = 500, message = "评分点不能超过 500 字符")
            String pointText,
            Integer sortNo
    ) {
    }
}
