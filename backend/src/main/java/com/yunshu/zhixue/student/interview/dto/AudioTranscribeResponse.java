package com.yunshu.zhixue.student.interview.dto;

/**
 * 语音转写结果（04 §6.7.7）：只转写、不落库、不推进轮次。
 *
 * <p>`asrDegraded=true` 时 `transcript` 为空串，前端必须回退到文字输入。
 * `durationSeconds` 由服务端在能解析时给出，不能解析时为 null。</p>
 */
public record AudioTranscribeResponse(
        int round,
        String transcript,
        boolean asrDegraded,
        boolean fallbackToText,
        Double durationSeconds
) {
}
