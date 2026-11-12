package com.yunshu.zhixue.admin.log;

import com.yunshu.zhixue.admin.log.dto.OperationLogItem;
import com.yunshu.zhixue.admin.log.dto.OperationLogPage;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.TimeFormats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * 操作日志查询（04 §6.10.10 / PRD 6.9）。
 *
 * <p>只读：不提供更新、删除、导出。游标分页以降序 id 为游标（日志表单调递增，id 即时间序），
 * 不返回 total，避免大表 COUNT。</p>
 */
@Service
public class AdminOperationLogService {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_SPAN_DAYS = 90;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String CURSOR_PREFIX = "v1:";

    /** 04 §6.10.10 模块白名单，与 V2/V3/V4 的 admin_role_module.module_code 一致 */
    private static final Set<String> MODULE_CODES = Set.of(
            "subject", "course", "question", "interview_settings", "learning_path",
            "student", "admin_user", "role", "operation_log", "dashboard", "notice", "message");

    private static final Set<String> RESULTS = Set.of("SUCCESS", "FAILED", "DENIED");

    private final JdbcTemplate jdbcTemplate;

    public AdminOperationLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public OperationLogPage list(String cursor, Integer limit, Long operatorId, String moduleCode,
                                 String action, String targetType, String targetId,
                                 String result, String startAt, String endAt) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "limit 必须在 1-" + MAX_LIMIT + " 之间");
        }

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();

        Long cursorId = decodeCursor(cursor);
        if (cursorId != null) {
            where.append(" AND id < ?");
            args.add(cursorId);
        }
        if (operatorId != null) {
            where.append(" AND operator_id = ?");
            args.add(operatorId);
        }
        if (moduleCode != null && !moduleCode.isBlank()) {
            String code = moduleCode.trim();
            if (!MODULE_CODES.contains(code)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "模块编码不在白名单内: " + code);
            }
            where.append(" AND module_code = ?");
            args.add(code);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action = ?");
            args.add(action.trim());
        }
        if (targetType != null && !targetType.isBlank()) {
            where.append(" AND target_type = ?");
            args.add(targetType.trim());
        }
        if (targetId != null && !targetId.isBlank()) {
            where.append(" AND target_id = ?");
            args.add(parseId(targetId));
        }
        if (result != null && !result.isBlank()) {
            String normalized = result.trim().toUpperCase();
            if (!RESULTS.contains(normalized)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "result 只能是 SUCCESS/FAILED/DENIED");
            }
            where.append(" AND result = ?");
            args.add(switch (normalized) {
                case "SUCCESS" -> 1;
                case "FAILED" -> 2;
                default -> 3;
            });
        }
        LocalDateTime start = parseInstant(startAt, "开始时间");
        LocalDateTime end = parseInstant(endAt, "结束时间");
        if (start != null && end != null) {
            if (end.isBefore(start)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "结束时间不能早于开始时间");
            }
            if (start.plusDays(MAX_SPAN_DAYS).isBefore(end)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "时间跨度不能超过 " + MAX_SPAN_DAYS + " 天");
            }
        }
        if (start != null) {
            where.append(" AND created_at >= ?");
            args.add(start);
        }
        if (end != null) {
            where.append(" AND created_at <= ?");
            args.add(end);
        }

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size + 1);

        List<OperationLogItem> rows = jdbcTemplate.query(
                "SELECT id, operator_id, operator_name, role_name, module_code, action, target_type, "
                        + "target_id, result, detail, ip, trace_id, created_at FROM operation_log"
                        + where + " ORDER BY id DESC LIMIT ?",
                (rs, rowNum) -> {
                    long target = rs.getLong("target_id");
                    Long targetValue = rs.wasNull() ? null : target;
                    return new OperationLogItem(
                            rs.getLong("id"),
                            rs.getLong("operator_id"),
                            rs.getString("operator_name"),
                            rs.getString("role_name"),
                            rs.getString("module_code"),
                            rs.getString("action"),
                            rs.getString("target_type"),
                            targetValue,
                            resultName(rs.getInt("result")),
                            rs.getString("detail"),
                            rs.getString("ip"),
                            rs.getString("trace_id"),
                            TimeFormats.rfc3339(rs.getObject("created_at")));
                },
                queryArgs.toArray());

        boolean hasMore = rows.size() > size;
        List<OperationLogItem> items = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore && !items.isEmpty()
                ? encodeCursor(items.get(items.size() - 1).id())
                : null;
        return new OperationLogPage(items, nextCursor, hasMore);
    }

    private static String encodeCursor(long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((CURSOR_PREFIX + id).getBytes(StandardCharsets.UTF_8));
    }

    private static Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            if (!raw.startsWith(CURSOR_PREFIX)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "cursor 不合法");
            }
            return Long.parseLong(raw.substring(CURSOR_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "cursor 不合法");
        }
    }

    /** 支持 RFC3339（带时区）与 yyyy-MM-dd；统一转 UTC 朴素时间与 DATETIME(3) 对齐 */
    private static LocalDateTime parseInstant(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.length() == 10) {
                return LocalDate.parse(text).atStartOfDay(BUSINESS_ZONE).withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
            }
            ZonedDateTime zoned = OffsetDateTime.parse(text).atZoneSameInstant(ZoneOffset.UTC);
            return zoned.toLocalDateTime();
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "格式应为 RFC3339 或 yyyy-MM-dd");
        }
    }

    private static long parseId(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "targetId 必须是数字");
        }
    }

    private static String resultName(int result) {
        return switch (result) {
            case 1 -> "SUCCESS";
            case 2 -> "FAILED";
            case 3 -> "DENIED";
            default -> "UNKNOWN";
        };
    }
}
