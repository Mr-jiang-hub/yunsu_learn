package com.yunshu.zhixue.student.wrongbook.dto;

import java.util.List;

/** 错题列表（04 §6.6）：偏移分页，只返回在册错题，按最近加入时间倒序。 */
public record WrongQuestionPage(
        List<WrongQuestionItem> items,
        int page,
        int size,
        long total,
        boolean hasNext
) {

    public record WrongQuestionItem(
            long questionId,
            String type,
            String contentPreview,
            long subjectId,
            String subjectName,
            long stageId,
            String stageName,
            String lastWrongAt,
            String addedAt
    ) {
    }
}
