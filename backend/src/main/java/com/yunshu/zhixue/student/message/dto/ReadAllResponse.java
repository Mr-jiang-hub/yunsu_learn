package com.yunshu.zhixue.student.message.dto;

/**
 * 全部已读结果。
 *
 * @param updated 本次被标记为已读的条数；此前已读的不重复计数
 */
public record ReadAllResponse(int updated) {
}
