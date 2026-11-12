package com.yunshu.zhixue.admin.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 编辑难度档位（04 §6.10.5）。禁止传 `code` 与 `status`；
 * 多余字段由 Jackson 反序列化报错 → 10002。
 */
public record UpdateDifficultyRequest(
        @Size(max = 20, message = "难度名称最长 20 个字符")
        String name,

        @Size(max = 500, message = "难度描述最长 500 个字符")
        String description,

        @Size(max = 500, message = "考察重点最长 500 个字符")
        String focus,

        @Min(value = 0, message = "排序号不能为负")
        @Max(value = 9999, message = "排序号不能超过 9999")
        Integer sortNo
) {
}
