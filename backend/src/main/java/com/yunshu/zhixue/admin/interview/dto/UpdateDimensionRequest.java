package com.yunshu.zhixue.admin.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 编辑维度（04 §6.10.5）。禁止传 `subjectId` 与 `status`；维度不跨学科移动。 */
public record UpdateDimensionRequest(
        @Size(max = 50, message = "维度名称最长 50 个字符")
        String name,

        @Size(max = 500, message = "维度说明最长 500 个字符")
        String description,

        @Min(value = 0, message = "排序号不能为负")
        @Max(value = 9999, message = "排序号不能超过 9999")
        Integer sortNo
) {
}
