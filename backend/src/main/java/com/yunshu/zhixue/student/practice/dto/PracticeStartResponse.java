package com.yunshu.zhixue.student.practice.dto;

import java.util.List;

/**
 * 练习创建响应（04 §6.5.1 / §6.5.2）。
 *
 * <p>`reused=true` 表示复用了该阶段/错题本已有的进行中练习，未新建；
 * 此时 `firstQuestion` 是当前待答题，字段名保持不变以免前端分支。</p>
 */
public record PracticeStartResponse(
        long practiceId,
        String type,
        String status,
        int questionCount,
        int answeredCount,
        String expireAt,
        boolean reused,
        QuestionView firstQuestion
) {

    /** 未作答题目：只给题干与选项，不含正确答案/解析/评分点 */
    public record QuestionView(
            long questionId,
            String type,
            String content,
            List<OptionView> options
    ) {
    }

    public record OptionView(long optionId, String content) {
    }
}
