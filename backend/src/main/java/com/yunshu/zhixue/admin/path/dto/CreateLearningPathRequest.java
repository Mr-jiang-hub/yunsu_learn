package com.yunshu.zhixue.admin.path.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 新增学习路线（04 §6.10.6）；一个学科仅允许一条路线 */
public record CreateLearningPathRequest(
        @NotNull(message = "所属学科不能为空")
        Long subjectId,
        @NotBlank(message = "路线名称不能为空")
        @Size(max = 50, message = "路线名称不能超过 50 字符")
        String name,
        @Size(max = 500, message = "路线简介不能超过 500 字符")
        String description,
        /*
         * learning_path 表无 sort_no 列：路线与学科 1:1，列表按学科 sort_no 排序。
         * 保留该字段仅为兼容 04 文档的请求签名，写入时忽略。
         */
        @Min(value = 0, message = "排序号不能为负")
        Integer sortNo
) {
}
