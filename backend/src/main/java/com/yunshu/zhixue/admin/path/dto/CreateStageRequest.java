package com.yunshu.zhixue.admin.path.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增阶段（04 §6.10.6）；路线内名称唯一，sortNo 缺省排到末尾 */
public record CreateStageRequest(
        @NotBlank(message = "阶段名称不能为空")
        @Size(max = 50, message = "阶段名称不能超过 50 字符")
        String name,
        @Size(max = 500, message = "阶段简介不能超过 500 字符")
        String description,
        @Min(value = 0, message = "排序号不能为负")
        Integer sortNo
) {
}
