package com.yunshu.zhixue.admin.subject;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.common.storage.CoverUrlResolver;
import com.yunshu.zhixue.admin.common.dto.SortOrderRequest;
import com.yunshu.zhixue.admin.subject.dto.CreateSubjectRequest;
import com.yunshu.zhixue.admin.subject.dto.SubjectItem;
import com.yunshu.zhixue.admin.subject.dto.UpdateSubjectRequest;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.util.TimeFormats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学科管理（04 §6.10.2 / PRD 6.2）。学科与学习路线一一对应，列表带 pathId/pathName。
 * 状态只走 enable/disable，PATCH 不接受 status。
 */
@Service
public class AdminSubjectService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 2;

    private static final String SELECT_BASE =
            "SELECT s.id, s.name, s.description, s.cover_url, s.sort_no, s.status, s.created_at, s.updated_at, "
                    + "p.id AS path_id, p.name AS path_name "
                    + "FROM subject s LEFT JOIN learning_path p ON p.subject_id = s.id ";

    private final JdbcTemplate jdbcTemplate;
    private final CoverUrlResolver coverUrlResolver;
    private final AdminAuditService auditService;

    public AdminSubjectService(JdbcTemplate jdbcTemplate, CoverUrlResolver coverUrlResolver,
                               AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.coverUrlResolver = coverUrlResolver;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageData<SubjectItem> list(int page, int size, String keyword, String status) {
        validatePaging(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND s.name LIKE ?");
            args.add("%" + escapeLike(keyword.trim()) + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND s.status = ?");
            args.add(parseStatus(status));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subject s " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);

        List<SubjectItem> items = jdbcTemplate.query(
                SELECT_BASE + where + " ORDER BY s.sort_no ASC, s.id ASC LIMIT ? OFFSET ?",
                rowMapper(), pageArgs.toArray());
        return PageData.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public SubjectItem detail(long id) {
        List<SubjectItem> rows = jdbcTemplate.query(SELECT_BASE + " WHERE s.id = ?", rowMapper(), id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        return rows.get(0);
    }

    @Transactional
    public SubjectItem create(CreateSubjectRequest request) {
        String name = request.name().trim();
        requireNameAvailable(name, null);
        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO subject (name, description, cover_url, sort_no, status, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, 1, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, request.description() == null ? "" : request.description().trim());
            statement.setString(3, request.coverKey() == null ? "" : request.coverKey().trim());
            statement.setInt(4, request.sortNo() == null ? 0 : request.sortNo());
            statement.setLong(5, adminId);
            statement.setLong(6, adminId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long newId = key == null ? 0 : key.longValue();
        auditService.record("subject", "create", "subject", newId, "新增学科 name=" + name);
        return detail(newId);
    }

    @Transactional
    public SubjectItem update(long id, UpdateSubjectRequest request) {
        detail(id);
        StringBuilder sql = new StringBuilder("UPDATE subject SET updated_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(CurrentUserContext.require(Audience.ADMIN).id());

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "学科名称不能为空");
            }
            requireNameAvailable(name, id);
            sql.append(", name = ?");
            args.add(name);
        }
        if (request.description() != null) {
            sql.append(", description = ?");
            args.add(request.description().trim());
        }
        if (request.coverKey() != null) {
            sql.append(", cover_url = ?");
            args.add(request.coverKey().trim());
        }
        if (request.sortNo() != null) {
            sql.append(", sort_no = ?");
            args.add(request.sortNo());
        }
        sql.append(" WHERE id = ?");
        args.add(id);

        if (args.size() > 2) {
            jdbcTemplate.update(sql.toString(), args.toArray());
            auditService.record("subject", "update", "subject", id, "编辑学科");
        }
        return detail(id);
    }

    @Transactional
    public void enable(long id) {
        changeStatus(id, STATUS_ENABLED, ErrorCode.ALREADY_ENABLED, "enable");
    }

    @Transactional
    public void disable(long id) {
        changeStatus(id, STATUS_DISABLED, ErrorCode.ALREADY_DISABLED, "disable");
    }

    @Transactional
    public void reorder(SortOrderRequest request) {
        Set<Long> seen = new HashSet<>();
        for (SortOrderRequest.Item item : request.items()) {
            if (!seen.add(item.id())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "排序列表存在重复 ID");
            }
        }
        for (SortOrderRequest.Item item : request.items()) {
            int updated = jdbcTemplate.update(
                    "UPDATE subject SET sort_no = ?, updated_by = ? WHERE id = ?",
                    item.sortNo(), CurrentUserContext.require(Audience.ADMIN).id(), item.id());
            if (updated == 0) {
                throw new BizException(ErrorCode.SUBJECT_NOT_FOUND);
            }
        }
        auditService.record("subject", "update", "subject", null,
                "调整学科排序，共 " + request.items().size() + " 条");
    }

    private void changeStatus(long id, int target, ErrorCode alreadyCode, String action) {
        Integer current = jdbcTemplate.query("SELECT status FROM subject WHERE id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, id);
        if (current == null) {
            throw new BizException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        if (current == target) {
            throw new BizException(alreadyCode);
        }
        jdbcTemplate.update("UPDATE subject SET status = ?, updated_by = ? WHERE id = ?",
                target, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("subject", action, "subject", id,
                "学科状态变更为 " + (target == STATUS_ENABLED ? "ENABLED" : "DISABLED"));
    }

    private void requireNameAvailable(String name, Long excludeId) {
        List<Long> rows = excludeId == null
                ? jdbcTemplate.queryForList("SELECT id FROM subject WHERE name = ?", Long.class, name)
                : jdbcTemplate.queryForList("SELECT id FROM subject WHERE name = ? AND id <> ?", Long.class, name, excludeId);
        if (!rows.isEmpty()) {
            throw new BizException(ErrorCode.SUBJECT_NAME_DUPLICATED);
        }
    }

    private RowMapper<SubjectItem> rowMapper() {
        return (rs, rowNum) -> {
            long pathId = rs.getLong("path_id");
            Long path = rs.wasNull() ? null : pathId;
            return new SubjectItem(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    coverUrlResolver.resolve(rs.getString("cover_url")),
                    rs.getInt("sort_no"),
                    rs.getInt("status") == STATUS_ENABLED ? "ENABLED" : "DISABLED",
                    path,
                    rs.getString("path_name"),
                    TimeFormats.rfc3339(rs.getObject("created_at")),
                    TimeFormats.rfc3339(rs.getObject("updated_at")));
        };
    }

    private static void validatePaging(int page, int size) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "页码必须大于等于 1");
        }
        if (size < 1 || size > PageQuery.MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "每页条数必须在 1-" + PageQuery.MAX_SIZE + " 之间");
        }
    }

    private static int parseStatus(String status) {
        return switch (status.trim().toUpperCase()) {
            case "ENABLED" -> STATUS_ENABLED;
            case "DISABLED" -> STATUS_DISABLED;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "状态取值只能是 ENABLED 或 DISABLED");
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 供课程模块复用：确认学科存在 */
    @Transactional(readOnly = true)
    public Map<String, Object> requireSubjectRow(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, status FROM subject WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        return rows.get(0);
    }
}
