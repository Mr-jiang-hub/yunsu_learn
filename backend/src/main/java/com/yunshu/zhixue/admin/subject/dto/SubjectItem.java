package com.yunshu.zhixue.admin.subject.dto;

/** 学科列表项（04 §6.10.2） */
public record SubjectItem(
        long id,
        String name,
        String description,
        String coverUrl,
        int sortNo,
        String status,
        Long pathId,
        String pathName,
        String createdAt,
        String updatedAt
) {
}
