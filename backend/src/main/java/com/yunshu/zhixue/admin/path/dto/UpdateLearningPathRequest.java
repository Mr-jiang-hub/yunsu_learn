package com.yunshu.zhixue.admin.path.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 编辑学习路线（04 §6.10.6）：禁止 subjectId / status */
public record UpdateLearningPathRequest(
        @Size(max = 50, message = "路线名称不能超过 50 字符")
        String name,
        @Size(max = 500, message = "路线简介不能超过 500 字符")
        String description,
        @Min(value = 0, message = "排序号不能为负")
        Integer sortNo
) {
}
