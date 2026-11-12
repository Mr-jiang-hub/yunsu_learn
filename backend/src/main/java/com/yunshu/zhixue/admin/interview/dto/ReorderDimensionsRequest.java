package com.yunshu.zhixue.admin.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 学科维度全量排序（04 §6.10.5）。`items` 必须覆盖该学科全部维度，
 * 缺项/重复/跨学科都会返回 40006。
 */
public record ReorderDimensionsRequest(
        @NotNull(message = "学科不能为空")
        Long subjectId,

        @NotEmpty(message = "排序列表不能为空")
        List<@Valid Item> items
) {
    public record Item(
            @NotNull(message = "维度 ID 不能为空")
            Long id,

            @NotNull(message = "排序号不能为空")
            @Min(value = 0, message = "排序号不能为负")
            Integer sortNo
    ) {
    }
}
