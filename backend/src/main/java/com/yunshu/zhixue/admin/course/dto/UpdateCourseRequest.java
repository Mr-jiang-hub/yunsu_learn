package com.yunshu.zhixue.admin.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 编辑课程（04 §6.10.3）：不传表示不改；禁止 status */
public record UpdateCourseRequest(
        @Size(max = 100, message = "课程标题不能超过 100 字符")
        String title,
        Long subjectId,
        Long stageId,
        @Size(max = 512, message = "B 站链接过长")
        String bilibiliUrl,
        @Size(max = 512, message = "封面 key 过长")
        String coverKey,
        @Size(max = 1000, message = "课程简介不能超过 1000 字符")
        String description,
        @Min(value = 0, message = "时长不能为负")
        Integer durationSeconds,
        @Min(value = 0, message = "排序号不能为负")
        Integer sortNo
) {
}
