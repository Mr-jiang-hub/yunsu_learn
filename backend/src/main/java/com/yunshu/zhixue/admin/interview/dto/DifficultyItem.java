package com.yunshu.zhixue.admin.interview.dto;

/**
 * 面试难度档位（04 §6.10.5）。
 *
 * <p>`code` 是稳定标识（easy/medium/hard），创建后不可改；`status` 仅能通过启停接口变更。</p>
 */
public record DifficultyItem(
        long id,
        String code,
        String name,
        String description,
        String focus,
        int sortNo,
        String status
) {
}
