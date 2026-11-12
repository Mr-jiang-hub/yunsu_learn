package com.yunshu.zhixue.admin.path;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.admin.common.dto.SortOrderRequest;
import com.yunshu.zhixue.admin.path.dto.CreateLearningPathRequest;
import com.yunshu.zhixue.admin.path.dto.CreateStageRequest;
import com.yunshu.zhixue.admin.path.dto.LearningPathDetail;
import com.yunshu.zhixue.admin.path.dto.LearningPathItem;
import com.yunshu.zhixue.admin.path.dto.StageContents;
import com.yunshu.zhixue.admin.path.dto.StageResponse;
import com.yunshu.zhixue.admin.path.dto.UpdateLearningPathRequest;
import com.yunshu.zhixue.admin.path.dto.UpdateStageRequest;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.util.TimeFormats;
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
 * 学习路线与阶段管理（04 §6.10.6 / PRD 6.5）。
 *
 * <p>约束：一个学科仅一条路线（表上 uk_learning_path_subject）；路线/阶段删除语义统一为停用，
 * 不物理删除，历史练习与面试的 stage_name 快照不受影响。PATCH 不接受 status，
 * subjectId 建后不可改。</p>
 */
@Service
public class AdminLearningPathService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 2;

    private static final int TYPE_TRUE_FALSE = 1;
    private static final int TYPE_SINGLE = 2;
    private static final int TYPE_MULTIPLE = 3;
    private static final int TYPE_SHORT_ANSWER = 4;

    private static final String SELECT_PATH =
            "SELECT p.id, p.name, p.description, p.status, p.created_at, p.updated_at, "
                    + "p.subject_id, s.name AS subject_name, s.sort_no AS subject_sort_no, "
                    + "(SELECT COUNT(*) FROM stage st WHERE st.path_id = p.id) AS stage_count "
                    + "FROM learning_path p JOIN subject s ON s.id = p.subject_id ";

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;

    public AdminLearningPathService(JdbcTemplate jdbcTemplate, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ 路线

    @Transactional(readOnly = true)
    public PageData<LearningPathItem> list(int page, int size, Long subjectId, String status) {
        validatePaging(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (subjectId != null) {
            where.append(" AND p.subject_id = ?");
            args.add(subjectId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND p.status = ?");
            args.add(parseStatus(status));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_path p" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);

        List<LearningPathItem> items = jdbcTemplate.query(
                SELECT_PATH + where + " ORDER BY s.sort_no ASC, p.id ASC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new LearningPathItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getLong("subject_id"),
                        rs.getString("subject_name"),
                        rs.getString("description"),
                        rs.getInt("status") == STATUS_ENABLED ? "ENABLED" : "DISABLED",
                        rs.getInt("stage_count"),
                        TimeFormats.rfc3339(rs.getObject("created_at")),
                        TimeFormats.rfc3339(rs.getObject("updated_at"))),
                pageArgs.toArray());
        return PageData.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public LearningPathDetail detail(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.id, p.name, p.description, p.status, p.created_at, p.updated_at, "
                        + "p.subject_id, s.name AS subject_name "
                        + "FROM learning_path p JOIN subject s ON s.id = p.subject_id WHERE p.id = ?", id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.LEARNING_PATH_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);

        return new LearningPathDetail(
                ((Number) row.get("id")).longValue(),
                (String) row.get("name"),
                ((Number) row.get("subject_id")).longValue(),
                (String) row.get("subject_name"),
                (String) row.get("description"),
                ((Number) row.get("status")).intValue() == STATUS_ENABLED ? "ENABLED" : "DISABLED",
                stageItems(id),
                TimeFormats.rfc3339(row.get("created_at")),
                TimeFormats.rfc3339(row.get("updated_at")));
    }

    @Transactional
    public LearningPathDetail create(CreateLearningPathRequest request) {
        String name = request.name().trim();
        long subjectId = request.subjectId();
        requireSubjectExists(subjectId);

        Long duplicated = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_path WHERE subject_id = ?", Long.class, subjectId);
        if (duplicated != null && duplicated > 0) {
            throw new BizException(ErrorCode.LEARNING_PATH_SUBJECT_DUPLICATED);
        }

        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO learning_path (subject_id, name, description, status, created_by, updated_by) "
                            + "VALUES (?, ?, ?, 1, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, subjectId);
            statement.setString(2, name);
            statement.setString(3, request.description() == null ? "" : request.description().trim());
            statement.setLong(4, adminId);
            statement.setLong(5, adminId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long newId = key == null ? 0 : key.longValue();
        auditService.record("learning_path", "create", "learning_path", newId,
                "新增学习路线 name=" + name + " subjectId=" + subjectId);
        return detail(newId);
    }

    @Transactional
    public LearningPathDetail update(long id, UpdateLearningPathRequest request) {
        detail(id);
        StringBuilder sql = new StringBuilder("UPDATE learning_path SET updated_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(CurrentUserContext.require(Audience.ADMIN).id());

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "路线名称不能为空");
            }
            sql.append(", name = ?");
            args.add(name);
        }
        if (request.description() != null) {
            sql.append(", description = ?");
            args.add(request.description().trim());
        }
        sql.append(" WHERE id = ?");
        args.add(id);

        if (args.size() > 2) {
            jdbcTemplate.update(sql.toString(), args.toArray());
            auditService.record("learning_path", "update", "learning_path", id, "编辑学习路线");
        }
        return detail(id);
    }

    @Transactional
    public void enable(long id) {
        changePathStatus(id, STATUS_ENABLED, ErrorCode.ALREADY_ENABLED, "enable");
    }

    @Transactional
    public void disable(long id) {
        changePathStatus(id, STATUS_DISABLED, ErrorCode.ALREADY_DISABLED, "disable");
    }

    // ------------------------------------------------------------------ 阶段

    @Transactional
    public StageResponse createStage(long pathId, CreateStageRequest request) {
        detail(pathId);
        String name = request.name().trim();
        requireStageNameAvailable(pathId, name, null);

        int sortNo = request.sortNo() != null ? request.sortNo() : nextStageSortNo(pathId);
        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO stage (path_id, name, description, sort_no, status, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, 1, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, pathId);
            statement.setString(2, name);
            statement.setString(3, request.description() == null ? "" : request.description().trim());
            statement.setInt(4, sortNo);
            statement.setLong(5, adminId);
            statement.setLong(6, adminId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long stageId = key == null ? 0 : key.longValue();
        auditService.record("learning_path", "create", "stage", stageId,
                "新增阶段 name=" + name + " pathId=" + pathId + " sortNo=" + sortNo);
        return stage(stageId);
    }

    @Transactional
    public StageResponse updateStage(long stageId, UpdateStageRequest request) {
        Map<String, Object> current = requireStageRow(stageId);
        long pathId = ((Number) current.get("path_id")).longValue();

        StringBuilder sql = new StringBuilder("UPDATE stage SET updated_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(CurrentUserContext.require(Audience.ADMIN).id());

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "阶段名称不能为空");
            }
            requireStageNameAvailable(pathId, name, stageId);
            sql.append(", name = ?");
            args.add(name);
        }
        if (request.description() != null) {
            sql.append(", description = ?");
            args.add(request.description().trim());
        }
        if (request.sortNo() != null) {
            sql.append(", sort_no = ?");
            args.add(request.sortNo());
        }
        sql.append(" WHERE id = ?");
        args.add(stageId);

        if (args.size() > 2) {
            jdbcTemplate.update(sql.toString(), args.toArray());
            auditService.record("learning_path", "update", "stage", stageId, "编辑阶段");
        }
        return stage(stageId);
    }

    @Transactional
    public void enableStage(long stageId) {
        changeStageStatus(stageId, STATUS_ENABLED, ErrorCode.ALREADY_ENABLED, "enable");
    }

    @Transactional
    public void disableStage(long stageId) {
        changeStageStatus(stageId, STATUS_DISABLED, ErrorCode.ALREADY_DISABLED, "disable");
    }

    /** 全量阶段排序：ID 必须属于该路线且不得重复、不得缺失（04 §6.10.6） */
    @Transactional
    public void reorderStages(long pathId, SortOrderRequest request) {
        detail(pathId);
        List<Long> owned = jdbcTemplate.queryForList(
                "SELECT id FROM stage WHERE path_id = ?", Long.class, pathId);
        Set<Long> ownedSet = new HashSet<>(owned);
        Set<Long> seen = new HashSet<>();
        for (SortOrderRequest.Item item : request.items()) {
            if (!seen.add(item.id())) {
                throw new BizException(ErrorCode.STAGE_ORDER_INVALID, "排序列表存在重复阶段");
            }
            if (!ownedSet.contains(item.id())) {
                throw new BizException(ErrorCode.STAGE_ORDER_INVALID, "存在不属于该路线的阶段");
            }
        }
        if (seen.size() != ownedSet.size()) {
            throw new BizException(ErrorCode.STAGE_ORDER_INVALID, "排序项缺失，须提交该路线的全部阶段");
        }
        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        for (SortOrderRequest.Item item : request.items()) {
            jdbcTemplate.update("UPDATE stage SET sort_no = ?, updated_by = ? WHERE id = ?",
                    item.sortNo(), adminId, item.id());
        }
        auditService.record("learning_path", "update", "stage", null,
                "调整阶段排序 pathId=" + pathId + "，共 " + request.items().size() + " 条");
    }

    /** 阶段已挂载的课程与题目（04 §6.10.6 GET /admin/stages/{stageId}/contents） */
    @Transactional(readOnly = true)
    public StageContents contents(long stageId) {
        requireStageRow(stageId);
        List<StageContents.CourseBrief> courses = jdbcTemplate.query(
                "SELECT id, title, bilibili_url, sort_no, status FROM course WHERE stage_id = ? "
                        + "ORDER BY sort_no ASC, id ASC",
                (rs, rowNum) -> new StageContents.CourseBrief(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("bilibili_url"),
                        rs.getInt("sort_no"),
                        rs.getInt("status") == STATUS_ENABLED ? "ENABLED" : "DISABLED"),
                stageId);
        List<StageContents.QuestionBrief> questions = jdbcTemplate.query(
                "SELECT id, type, status, LEFT(content, 120) AS content_preview FROM question "
                        + "WHERE stage_id = ? ORDER BY id ASC",
                (rs, rowNum) -> new StageContents.QuestionBrief(
                        rs.getLong("id"),
                        dbToType(rs.getInt("type")),
                        rs.getString("content_preview"),
                        rs.getInt("status") == STATUS_ENABLED ? "ENABLED" : "DISABLED"),
                stageId);
        return new StageContents(courses, questions);
    }

    // ------------------------------------------------------------------ 内部

    private List<LearningPathDetail.StageItem> stageItems(long pathId) {
        return jdbcTemplate.query(
                "SELECT st.id, st.name, st.description, st.sort_no, st.status, "
                        + "(SELECT COUNT(*) FROM course c WHERE c.stage_id = st.id) AS course_count, "
                        + "(SELECT COUNT(*) FROM question q WHERE q.stage_id = st.id) AS question_count "
                        + "FROM stage st WHERE st.path_id = ? ORDER BY st.sort_no ASC, st.id ASC",
                (rs, rowNum) -> new LearningPathDetail.StageItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("sort_no"),
                        rs.getInt("status") == STATUS_ENABLED ? "ENABLED" : "DISABLED",
                        rs.getInt("course_count"),
                        rs.getInt("question_count")),
                pathId);
    }

    private StageResponse stage(long stageId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT st.id, st.path_id, st.name, st.description, st.sort_no, st.status, "
                        + "st.created_at, st.updated_at, "
                        + "(SELECT COUNT(*) FROM course c WHERE c.stage_id = st.id) AS course_count, "
                        + "(SELECT COUNT(*) FROM question q WHERE q.stage_id = st.id) AS question_count "
                        + "FROM stage st WHERE st.id = ?", stageId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.STAGE_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);
        return new StageResponse(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("path_id")).longValue(),
                (String) row.get("name"),
                (String) row.get("description"),
                ((Number) row.get("sort_no")).intValue(),
                ((Number) row.get("status")).intValue() == STATUS_ENABLED ? "ENABLED" : "DISABLED",
                ((Number) row.get("course_count")).intValue(),
                ((Number) row.get("question_count")).intValue(),
                TimeFormats.rfc3339(row.get("created_at")),
                TimeFormats.rfc3339(row.get("updated_at")));
    }

    private Map<String, Object> requireStageRow(long stageId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, path_id, name, status FROM stage WHERE id = ?", stageId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.STAGE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private void requireSubjectExists(long subjectId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subject WHERE id = ?", Long.class, subjectId);
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.SUBJECT_NOT_FOUND);
        }
    }

    private void requireStageNameAvailable(long pathId, String name, Long excludeId) {
        List<Long> rows = excludeId == null
                ? jdbcTemplate.queryForList(
                        "SELECT id FROM stage WHERE path_id = ? AND name = ?", Long.class, pathId, name)
                : jdbcTemplate.queryForList(
                        "SELECT id FROM stage WHERE path_id = ? AND name = ? AND id <> ?",
                        Long.class, pathId, name, excludeId);
        if (!rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该路线下已存在同名阶段");
        }
    }

    private int nextStageSortNo(long pathId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_no), 0) FROM stage WHERE path_id = ?", Integer.class, pathId);
        return (max == null ? 0 : max) + 10;
    }

    private void changePathStatus(long id, int target, ErrorCode alreadyCode, String action) {
        Integer current = jdbcTemplate.query("SELECT status FROM learning_path WHERE id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, id);
        if (current == null) {
            throw new BizException(ErrorCode.LEARNING_PATH_NOT_FOUND);
        }
        if (current == target) {
            throw new BizException(alreadyCode);
        }
        jdbcTemplate.update("UPDATE learning_path SET status = ?, updated_by = ? WHERE id = ?",
                target, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("learning_path", action, "learning_path", id,
                "路线状态变更为 " + (target == STATUS_ENABLED ? "ENABLED" : "DISABLED"));
    }

    private void changeStageStatus(long stageId, int target, ErrorCode alreadyCode, String action) {
        Map<String, Object> row = requireStageRow(stageId);
        int current = ((Number) row.get("status")).intValue();
        if (current == target) {
            throw new BizException(alreadyCode);
        }
        jdbcTemplate.update("UPDATE stage SET status = ?, updated_by = ? WHERE id = ?",
                target, CurrentUserContext.require(Audience.ADMIN).id(), stageId);
        auditService.record("learning_path", action, "stage", stageId,
                "阶段状态变更为 " + (target == STATUS_ENABLED ? "ENABLED" : "DISABLED"));
    }

    private static String dbToType(int type) {
        return switch (type) {
            case TYPE_TRUE_FALSE -> "TRUE_FALSE";
            case TYPE_SINGLE -> "SINGLE_CHOICE";
            case TYPE_MULTIPLE -> "MULTIPLE_CHOICE";
            case TYPE_SHORT_ANSWER -> "SHORT_ANSWER";
            default -> "UNKNOWN";
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
}
