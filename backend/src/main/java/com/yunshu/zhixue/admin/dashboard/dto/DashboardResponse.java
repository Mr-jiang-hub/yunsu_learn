package com.yunshu.zhixue.admin.dashboard.dto;

import java.util.List;
import java.util.Map;

/**
 * 数据看板（04 §6.10.11）。各区块独立取数，单区块失败置 null 并记入 degradedSections，
 * 不整包失败。
 */
public record DashboardResponse(
        String range,
        String startDate,
        String endDate,
        Map<String, Object> sections,
        Meta meta
) {

    public record Meta(String dataAsOf, int cacheTtlSeconds, List<String> degradedSections) {
    }
}
