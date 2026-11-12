package com.yunshu.zhixue.admin.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 新增维度（04 §6.10.5）：学科内名称唯一。 */
public record CreateDimensionRequest(
        @NotNull(message = "学科不能为空")
        Long subjectId,

        @NotBlank(message = "维度名称不能为空")
        @Size(max = 50, message = "维度名称最长 50 个字符")
        String name,

        @Size(max = 500, message = "维度说明最长 500 个字符")
        String description,

        @Min(value = 0, message = "排序号不能为负")
        @Max(value = 9999, message = "排序号不能超过 9999")
        Integer sortNo
) {
}
