package com.yunshu.zhixue.admin.log.dto;

/** 单条操作日志（04 §6.10.10）：detail 不返回密码/验证码/token/完整手机号 */
public record OperationLogItem(
        long id,
        long operatorId,
        String operatorName,
        String roleName,
        String moduleCode,
        String action,
        String targetType,
        Long targetId,
        String result,
        String detail,
        String ip,
        String traceId,
        String createdAt
) {
}
