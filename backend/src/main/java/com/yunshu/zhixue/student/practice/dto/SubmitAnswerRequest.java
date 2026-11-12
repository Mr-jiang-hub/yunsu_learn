package com.yunshu.zhixue.student.practice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 提交单题作答（04 §6.5.4）。
 *
 * <p>客观题必填 `selectedOptionIds`（单选只能 1 个），简答题必填 `answerText`（1–2000）。
 * `clientRequestId` 仅用于日志关联，不作为幂等依据——幂等由 `(practiceId, questionId)` 唯一键保证。</p>
 */
public record SubmitAnswerRequest(
        @NotNull(message = "题目不能为空")
        Long questionId,

        List<Long> selectedOptionIds,

        @Size(max = 2000, message = "答案最长 2000 个字符")
        String answerText,

        String source,

        String clientRequestId
) {
}
