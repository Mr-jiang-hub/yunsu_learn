package com.yunshu.zhixue.admin.interview;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.admin.interview.dto.CreateDimensionRequest;
import com.yunshu.zhixue.admin.interview.dto.DimensionItem;
import com.yunshu.zhixue.admin.interview.dto.DifficultyItem;
import com.yunshu.zhixue.admin.interview.dto.ReorderDimensionsRequest;
import com.yunshu.zhixue.admin.interview.dto.UpdateDifficultyRequest;
import com.yunshu.zhixue.admin.interview.dto.UpdateDimensionRequest;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * 面试设置（04 §6.10.5 / PRD 6.6）。
 *
 * <p>难度档位是全局配置（Q29 已确认，不按学科拆分），固定 3 档，只能改文案与启停，
 * `code` 是稳定标识不可改；能力维度按学科维护，被报告引用后只停用不删除。
 * 停用不物理删除：历史会话与报告保留难度/维度快照。</p>
 */
@Service
public class AdminInterviewService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 2;

    private static final String SELECT_DIFFICULTY =
            "SELECT id, code, name, description, focus, sort_no, status FROM interview_difficulty ";

    private static final String SELECT_DIMENSION =
            "SELECT id, subject_id, name, sort_no, status FROM dimension ";

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;

    public AdminInterviewService(JdbcTemplate jdbcTemplate, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ 难度

    @Transactional(readOnly = true)
    public List<DifficultyItem> listDifficulties() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_DIFFICULTY + "ORDER BY sort_no ASC, id ASC");
        List<DifficultyItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(toDifficulty(row));
        }
        return items;
    }

    @Transactional
    public DifficultyItem updateDifficulty(long id, UpdateDifficultyRequest request) {
        requireDifficulty(id);
        StringBuilder sql = new StringBuilder("UPDATE interview_difficulty SET updated_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(CurrentUserContext.require(Audience.ADMIN).id());

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "难度名称不能为空");
            }
            requireDifficultyNameAvailable(name, id);
            sql.append(", name = ?");
            args.add(name);
        }
        if (request.description() != null) {
            sql.append(", description = ?");
            args.add(request.description().trim());
        }
        if (request.focus() != null) {
            sql.append(", focus = ?");
            args.add(request.focus().trim());
        }
        if (request.sortNo() != null) {
            sql.append(", sort_no = ?");
            args.add(request.sortNo());
        }
        sql.append(" WHERE id = ?");
        args.add(id);

        if (args.size() > 2) {
            jdbcTemplate.update(sql.toString(), args.toArray());
            auditService.record("interview_settings", "update", "interview_difficulty", id,
                    "编辑难度档位 id=" + id);
        }
        return difficulty(id);
    }

    @Transactional
    public void enableDifficulty(long id) {
        changeDifficultyStatus(id, STATUS_ENABLED, ErrorCode.ALREADY_ENABLED, "enable");
    }

    @Transactional
    public void disableDifficulty(long id) {
        changeDifficultyStatus(id, STATUS_DISABLED, ErrorCode.ALREADY_DISABLED, "disable");
    }

    // ------------------------------------------------------------------ 维度

    @Transactional(readOnly = true)
    public List<DimensionItem> listDimensions(long subjectId) {
        requireSubject(subjectId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_DIMENSION + "WHERE subject_id = ? ORDER BY sort_no ASC, id ASC", subjectId);
        List<DimensionItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(toDimension(row));
        }
        return items;
    }

    @Transactional
    public DimensionItem createDimension(CreateDimensionRequest request) {
        requireSubject(request.subjectId());
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "维度名称不能为空");
        }
        requireDimensionNameAvailable(request.subjectId(), name, null);

        int sortNo = request.sortNo() == null ? nextDimensionSortNo(request.subjectId()) : request.sortNo();
        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO dimension (subject_id, name, sort_no, status, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, request.subjectId());
            ps.setString(2, name);
            ps.setInt(3, sortNo);
            ps.setInt(4, STATUS_ENABLED);
            ps.setLong(5, adminId);
            ps.setLong(6, adminId);
            return ps;
        }, keyHolder);
        long id = requireGeneratedId(keyHolder);

        auditService.record("interview_settings", "create", "dimension", id,
                "新增维度「" + name + "」subjectId=" + request.subjectId());
        return dimension(id);
    }

    @Transactional
    public DimensionItem updateDimension(long id, UpdateDimensionRequest request) {
        DimensionItem existing = dimension(id);
        StringBuilder sql = new StringBuilder("UPDATE dimension SET updated_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(CurrentUserContext.require(Audience.ADMIN).id());

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "维度名称不能为空");
            }
            requireDimensionNameAvailable(existing.subjectId(), name, id);
            sql.append(", name = ?");
            args.add(name);
        }
        if (request.sortNo() != null) {
            sql.append(", sort_no = ?");
            args.add(request.sortNo());
        }
        sql.append(" WHERE id = ?");
        args.add(id);

        if (args.size() > 2) {
            jdbcTemplate.update(sql.toString(), args.toArray());
            auditService.record("interview_settings", "update", "dimension", id,
                    "编辑维度 id=" + id);
        }
        return dimension(id);
    }

    @Transactional
    public void enableDimension(long id) {
        changeDimensionStatus(id, STATUS_ENABLED, ErrorCode.ALREADY_ENABLED, "enable");
    }

    @Transactional
    public void disableDimension(long id) {
        changeDimensionStatus(id, STATUS_DISABLED, ErrorCode.ALREADY_DISABLED, "disable");
    }

    /** 学科维度全量排序：ID 必须全部属于该学科且不得重复、不得缺项（04 §6.10.5） */
    @Transactional
    public void reorderDimensions(ReorderDimensionsRequest request) {
        requireSubject(request.subjectId());
        List<Long> owned = jdbcTemplate.queryForList(
                "SELECT id FROM dimension WHERE subject_id = ?", Long.class, request.subjectId());
        Set<Long> ownedSet = new HashSet<>(owned);
        Set<Long> seen = new HashSet<>();
        for (ReorderDimensionsRequest.Item item : request.items()) {
            if (!seen.add(item.id())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "排序列表存在重复维度");
            }
            if (!ownedSet.contains(item.id())) {
                throw new BizException(ErrorCode.DIMENSION_NOT_FOUND, "存在不属于该学科的维度");
            }
        }
        if (seen.size() != ownedSet.size()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "排序列表必须包含该学科全部维度");
        }
        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        for (ReorderDimensionsRequest.Item item : request.items()) {
            jdbcTemplate.update("UPDATE dimension SET sort_no = ?, updated_by = ? WHERE id = ?",
                    item.sortNo(), adminId, item.id());
        }
        auditService.record("interview_settings", "update", "dimension", null,
                "调整维度排序 subjectId=" + request.subjectId() + "，共 " + request.items().size() + " 条");
    }

    // ------------------------------------------------------------------ 内部

    private void changeDifficultyStatus(long id, int target, ErrorCode already, String action) {
        int current = "ENABLED".equals(requireDifficulty(id).status()) ? STATUS_ENABLED : STATUS_DISABLED;
        if (current == target) {
            throw new BizException(already);
        }
        jdbcTemplate.update("UPDATE interview_difficulty SET status = ?, updated_by = ? WHERE id = ?",
                target, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("interview_settings", action, "interview_difficulty", id,
                (target == STATUS_ENABLED ? "启用" : "停用") + "难度档位 id=" + id);
    }

    private void changeDimensionStatus(long id, int target, ErrorCode already, String action) {
        DimensionItem existing = dimension(id);
        int current = "ENABLED".equals(existing.status()) ? STATUS_ENABLED : STATUS_DISABLED;
        if (current == target) {
            throw new BizException(already);
        }
        jdbcTemplate.update("UPDATE dimension SET status = ?, updated_by = ? WHERE id = ?",
                target, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("interview_settings", action, "dimension", id,
                (target == STATUS_ENABLED ? "启用" : "停用") + "维度「" + existing.name() + "」");
    }

    private DifficultyItem requireDifficulty(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_DIFFICULTY + "WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.DIFFICULTY_NOT_FOUND);
        }
        return toDifficulty(rows.get(0));
    }

    private DifficultyItem difficulty(long id) {
        return requireDifficulty(id);
    }
    private DimensionItem dimension(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_DIMENSION + "WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.DIMENSION_NOT_FOUND);
        }
        return toDimension(rows.get(0));
    }

    private void requireDifficultyNameAvailable(String name, Long excludeId) {
        List<Long> ids = excludeId == null
                ? jdbcTemplate.queryForList(
                        "SELECT id FROM interview_difficulty WHERE name = ?", Long.class, name)
                : jdbcTemplate.queryForList(
                        "SELECT id FROM interview_difficulty WHERE name = ? AND id <> ?",
                        Long.class, name, excludeId);
        if (!ids.isEmpty()) {
            throw new BizException(ErrorCode.DIFFICULTY_DUPLICATED);
        }
    }

    private void requireDimensionNameAvailable(long subjectId, String name, Long excludeId) {
        List<Long> ids = excludeId == null
                ? jdbcTemplate.queryForList(
                        "SELECT id FROM dimension WHERE subject_id = ? AND name = ?",
                        Long.class, subjectId, name)
                : jdbcTemplate.queryForList(
                        "SELECT id FROM dimension WHERE subject_id = ? AND name = ? AND id <> ?",
                        Long.class, subjectId, name, excludeId);
        if (!ids.isEmpty()) {
            throw new BizException(ErrorCode.DIMENSION_NAME_DUPLICATED);
        }
    }

    private void requireSubject(long subjectId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subject WHERE id = ?", Integer.class, subjectId);
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.SUBJECT_NOT_FOUND);
        }
    }

    private int nextDimensionSortNo(long subjectId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_no), 0) FROM dimension WHERE subject_id = ?",
                Integer.class, subjectId);
        return (max == null ? 0 : max) + 10;
    }

    private static long requireGeneratedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "新增维度未取得主键");
        }
        return key.longValue();
    }

    private static DifficultyItem toDifficulty(Map<String, Object> row) {
        return new DifficultyItem(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("code")),
                String.valueOf(row.get("name")),
                nullToEmpty(row.get("description")),
                nullToEmpty(row.get("focus")),
                ((Number) row.get("sort_no")).intValue(),
                statusText(row.get("status")));
    }

    private static DimensionItem toDimension(Map<String, Object> row) {
        return new DimensionItem(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("subject_id")).longValue(),
                String.valueOf(row.get("name")),
                "",
                ((Number) row.get("sort_no")).intValue(),
                statusText(row.get("status")));
    }

    private static String statusText(Object value) {
        int code = value instanceof Number number ? number.intValue() : STATUS_ENABLED;
        return code == STATUS_DISABLED ? "DISABLED" : "ENABLED";
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
