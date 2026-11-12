package com.yunshu.zhixue.student.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交一轮作答（04 §6.7.6）。语音转写后可编辑，P0 一律以文本落库。
 */
public record SubmitAnswerRequest(
        @NotNull(message = "轮次不能为空")
        @Min(value = 1, message = "轮次范围为 1-4")
        @Max(value = 4, message = "轮次范围为 1-4")
        Integer round,
        @NotBlank(message = "回答内容不能为空")
        @Size(max = 2000, message = "回答内容不能超过 2000 字")
        String answerText,
        String source,
        String clientRequestId
) {
}
