package com.yunshu.zhixue.integration.ai;

/**
 * 语音识别结果。degraded=true 表示 ASR 失败，前端据此切换文字输入，
 * 不得让面试会话卡死（api-rules 11.2）。
 */
public record AsrResponse(String transcript, boolean degraded) {
}
