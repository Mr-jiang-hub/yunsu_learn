package com.yunshu.zhixue.admin.subject.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增学科（04 §6.10.2） */
public record CreateSubjectRequest(
        @NotBlank(message = "学科名称不能为空")
        @Size(max = 50, message = "学科名称不能超过 50 字符")
        String name,
        @Size(max = 500, message = "学科简介不能超过 500 字符")
        String description,
        @Size(max = 512, message = "封面 key 过长")
        String coverKey,
        @Min(value = 0, message = "排序号不能为负")
        Integer sortNo
) {
}
