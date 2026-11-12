package com.yunshu.zhixue.student.learning.dto;

import java.util.List;

/**
 * 路线详情（04 §6.3）。
 *
 * <p>`currentStageId` 取第一个"启用且有未完成课程"的阶段；全部完成时取最后一个启用阶段，
 * 无启用阶段为 null，前端据此定位"继续学习"。</p>
 */
public record PathDetailResponse(
        PathBrief path,
        Long currentStageId,
        List<PathStageItem> stages
) {

    public record PathBrief(
            long pathId,
            String pathName,
            long subjectId,
            String subjectName,
            String description,
            int stageCount,
            int courseCount,
            int questionCount,
            double progressRate,
            double masteryRate,
            boolean isCurrent
    ) {
    }
}
