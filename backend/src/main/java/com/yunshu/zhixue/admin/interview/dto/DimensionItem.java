package com.yunshu.zhixue.admin.interview.dto;

/**
 * 学科能力维度（04 §6.10.5）。
 *
 * <p>04 文档在 `description` 上要求可读写，但 03 §4.3 的 `dimension` 表没有该列，
 * 本期按"接受但不落库"处理：出参固定返回空串，入参允许传入以免前端报错。</p>
 */
public record DimensionItem(
        long id,
        long subjectId,
        String name,
        String description,
        int sortNo,
        String status
) {
}
