package com.yunshu.zhixue.admin.system.dto;

import java.util.List;

/**
 * 孤儿文件巡检结果（04-Q47 P0 方案 A：只巡检、只报告，不自动删除）。
 *
 * @param prefixes       按业务前缀分组的统计
 * @param items          孤儿候选明细；按前缀与 key 升序
 * @param truncated      明细是否被截断（超过展示上限）
 * @param scannedObjects 本次扫到的对象总数
 * @param orphanCount    孤儿候选总数
 * @param orphanBytes    孤儿候选总字节数
 * @param scannedAt      巡检时间（RFC 3339，UTC）
 */
public record OrphanFileReport(
        List<PrefixSummary> prefixes,
        List<OrphanItem> items,
        boolean truncated,
        long scannedObjects,
        long orphanCount,
        long orphanBytes,
        String scannedAt
) {

    /** 单个业务前缀的巡检统计 */
    public record PrefixSummary(String prefix, long objectCount, long orphanCount, long orphanBytes) {
    }

    /** 一条孤儿候选；只给 key 不给 URL，删除前必须人工确认 */
    public record OrphanItem(String key, String prefix, long size, String lastModified) {
    }
}
