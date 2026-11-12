package com.yunshu.zhixue.student.interview.dto;

/** 提前结束（04 §6.7.8）：`confirm=false` 且当前轮未提交时返回 30054。 */
public record FinishRequest(Boolean confirm) {

    public boolean confirmed() {
        return Boolean.TRUE.equals(confirm);
    }
}
