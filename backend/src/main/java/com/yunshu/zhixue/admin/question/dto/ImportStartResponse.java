package com.yunshu.zhixue.admin.question.dto;

/** 导入任务创建结果（04 §6.10.4）：异步，立刻返回 taskId 供轮询 */
public record ImportStartResponse(long taskId, String status, String createdAt) {
}
