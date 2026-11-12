package com.yunshu.zhixue.admin.system;

import com.yunshu.zhixue.admin.system.dto.OrphanFileReport;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.integration.storage.ObjectStorage;
import com.yunshu.zhixue.integration.storage.StorageException;
import com.yunshu.zhixue.integration.storage.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 孤儿文件巡检（04-Q47 P0 方案 A）。
 *
 * <p>库里只有三列承接对象 key：`subject.cover_url`、`course.cover_url`、`student.avatar_url`。
 * 上传成功但未绑定（或绑定后被覆盖/清空）的 key 就是孤儿候选。P0 没有文件元数据表，
 * 无法按"未绑定超 24 小时"精确判定，因此这里只做**只读巡检 + 人工确认**：
 * 列出对象、与库中被引用 key 求差集，**绝不删除任何对象**。</p>
 *
 * <p>判定是"当前快照"语义：刚上传还没来得及 PATCH 的 key 也会出现在候选里，
 * 所以结果只能人工确认后再处理，不能接自动清理。</p>
 */
@Service
public class OrphanFileService {

    private static final Logger log = LoggerFactory.getLogger(OrphanFileService.class);

    /** 业务前缀 → 该前缀下 key 的引用来源表与列 */
    private static final Map<String, Reference> REFERENCES = Map.of(
            "subject", new Reference("subject", "cover_url"),
            "course", new Reference("course", "cover_url"),
            "avatar", new Reference("student", "avatar_url"));

    /** 明细展示上限；超出只截断明细，统计值仍是全量 */
    private static final int MAX_ITEMS = 200;

    private final ObjectStorage objectStorage;
    private final JdbcTemplate jdbcTemplate;

    public OrphanFileService(ObjectStorage objectStorage, JdbcTemplate jdbcTemplate) {
        this.objectStorage = objectStorage;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 只读巡检：列出三个业务前缀下未被任何库表引用的对象 */
    public OrphanFileReport inspect() {
        List<OrphanFileReport.PrefixSummary> summaries = new ArrayList<>();
        List<OrphanFileReport.OrphanItem> items = new ArrayList<>();
        long scanned = 0;
        long orphanCount = 0;
        long orphanBytes = 0;

        for (Map.Entry<String, Reference> entry : REFERENCES.entrySet()) {
            String prefix = entry.getKey();
            Set<String> referenced = loadReferenced(entry.getValue());
            List<StoredObject> objects;
            try {
                objects = objectStorage.list(prefix);
            } catch (StorageException ex) {
                // 单个前缀列举失败不阻断其它前缀：记录告警并跳过，避免运维看不到其余结果
                log.warn("孤儿文件巡检跳过前缀 {}: 列举失败", prefix, ex);
                summaries.add(new OrphanFileReport.PrefixSummary(prefix, 0, 0, 0));
                continue;
            }
            long prefixOrphanCount = 0;
            long prefixOrphanBytes = 0;
            for (StoredObject object : objects) {
                if (referenced.contains(object.key())) {
                    continue;
                }
                prefixOrphanCount++;
                prefixOrphanBytes += object.size();
                if (items.size() < MAX_ITEMS) {
                    items.add(new OrphanFileReport.OrphanItem(
                            object.key(),
                            prefix,
                            object.size(),
                            object.lastModified() == null ? null : TimeFormats.rfc3339(object.lastModified())));
                }
            }
            scanned += objects.size();
            orphanCount += prefixOrphanCount;
            orphanBytes += prefixOrphanBytes;
            summaries.add(new OrphanFileReport.PrefixSummary(prefix, objects.size(), prefixOrphanCount, prefixOrphanBytes));
        }

        items.sort(Comparator.comparing(OrphanFileReport.OrphanItem::key));
        return new OrphanFileReport(summaries, items, orphanCount > MAX_ITEMS,
                scanned, orphanCount, orphanBytes, TimeFormats.rfc3339(Instant.now()));
    }

    /**
     * 每日定时巡检：只打日志摘要，不做任何删除（04-Q47 明确"不自动删除可能被引用的对象"）。
     * 单月候选超过 100 个是 P1-01 新增 `file_asset` 表的触发条件（05 §11）。
     */
    @Scheduled(cron = "${storage.orphan-inspection.cron:0 0 3 * * *}")
    public void scheduledInspect() {
        try {
            OrphanFileReport report = inspect();
            if (report.orphanCount() == 0) {
                log.info("孤儿文件巡检完成: 扫描 {} 个对象，无孤儿候选", report.scannedObjects());
                return;
            }
            log.warn("孤儿文件巡检发现 {} 个候选（{} 字节），扫描 {} 个对象，需人工确认后处理；明细 {} 条{}",
                    report.orphanCount(), report.orphanBytes(), report.scannedObjects(), report.items().size(),
                    report.truncated() ? "（已截断）" : "");
        } catch (RuntimeException ex) {
            // 巡检是旁路能力，失败不能影响主营业务，只告警
            log.warn("孤儿文件定时巡检失败", ex);
        }
    }

    private Set<String> loadReferenced(Reference reference) {
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT " + reference.column() + " FROM " + reference.table()
                        + " WHERE " + reference.column() + " IS NOT NULL AND " + reference.column() + " <> ''",
                String.class);
        return new HashSet<>(keys);
    }

    private record Reference(String table, String column) {
    }
}
