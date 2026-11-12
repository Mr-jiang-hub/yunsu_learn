package com.yunshu.zhixue.admin.path.dto;

import java.util.List;

/** 阶段已挂载内容（04 §6.10.6 GET /admin/stages/{stageId}/contents） */
public record StageContents(
        List<CourseBrief> courses,
        List<QuestionBrief> questions
) {

    public record CourseBrief(
            long id,
            String title,
            String bilibiliUrl,
            int sortNo,
            String status
    ) {
    }

    public record QuestionBrief(
            long id,
            String type,
            String contentPreview,
            String status
    ) {
    }
}
