package com.yunshu.zhixue.student.practice.dto;

/** 创建错题重练（04 §6.5.2）：筛选条件均可选，空则取全部在册错题。 */
public record WrongRetryRequest(
        Long subjectId,
        Long stageId
) {
}
