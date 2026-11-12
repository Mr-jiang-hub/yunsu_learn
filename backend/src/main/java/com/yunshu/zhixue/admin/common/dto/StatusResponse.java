package com.yunshu.zhixue.admin.common.dto;

/** 启停类动作的统一响应（04 §6.10 B 端约定：返回 {id,status}） */
public record StatusResponse(long id, String status) {
}
