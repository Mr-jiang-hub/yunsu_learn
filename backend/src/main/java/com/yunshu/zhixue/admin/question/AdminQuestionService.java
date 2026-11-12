package com.yunshu.zhixue.admin.question;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.admin.question.dto.CreateQuestionRequest;
import com.yunshu.zhixue.admin.question.dto.QuestionDetail;
import com.yunshu.zhixue.admin.question.dto.QuestionListItem;
import com.yunshu.zhixue.admin.question.dto.UpdateQuestionRequest;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 题库管理（04 §6.10.4 / PRD 6.4）。选项与评分点拆子表；
 * 题目重复按「同阶段 + 规范化题干 SHA-256」判定。
 *
 * <p>本轮未接入作答模块，PATCH 的选项为全量替换；作答功能上线后需补
 * 「已被作答引用的选项不得删除」校验（04 §6.10.4.1）。</p>
 */
@Service
public class AdminQuestionService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 2;
    private static final int TYPE_TRUE_FALSE = 1;
    private static final int TYPE_SINGLE = 2;
    private static final int TYPE_MULTIPLE = 3;
    private static final int TYPE_SHORT_ANSWER = 4;

    private static final String SELECT_LIST =
            "SELECT q.id, q.type, LEFT(q.content, 60) AS content_preview, q.status, q.created_at, q.updated_at, "
                    + "q.stage_id, st.name AS stage_name, p.subject_id, s.name AS subject_name "
                    + "FROM question q "
                    + "JOIN stage st ON st.id = q.stage_id "
                    + "JOIN learning_path p ON p.id = st.path_id "
                    + "JOIN subject s ON s.id = p.subject_id ";

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;

    public AdminQuestionService(JdbcTemplate jdbcTemplate, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageData<QuestionListItem> list(int page, int size, Long subjectId, Long stageId,
                                           String type, String keyword, String status) {
        validatePaging(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (subjectId != null) {
            where.append(" AND p.subject_id = ?");
            args.add(subjectId);
        }
        if (stageId != null) {
            where.append(" AND q.stage_id = ?");
            args.add(stageId);
        }
        if (type != null && !type.isBlank()) {
            where.append(" AND q.type = ?");
            args.add(typeToDb(type));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND q.content LIKE ?");
            args.add("%" + escapeLike(keyword.trim()) + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND q.status = ?");
            args.add(parseStatus(status));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question q "
                        + "JOIN stage st ON st.id = q.stage_id "
                        + "JOIN learning_path p ON p.id = st.path_id " + where,
                Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);
        List<QuestionListItem> items = jdbcTemplate.query(
                SELECT_LIST + where + " ORDER BY q.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new QuestionListItem(
                        rs.getLong("id"),
                        dbToType(rs.getInt("type")),
                        rs.getString("content_preview"),
                        rs.getLong("subject_id"),
                        rs.getString("subject_name"),
                        rs.getLong("stage_id"),
                        rs.getString("stage_name"),
                        rs.getInt("status") == STATUS_ENABLED ? "ENABLED" : "DISABLED",
                        TimeFormats.rfc3339(rs.getObject("created_at")),
                        TimeFormats.rfc3339(rs.getObject("updated_at"))),
                pageArgs.toArray());
        return PageData.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public QuestionDetail detail(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT q.id, q.type, q.content, q.analysis, q.reference_answer, q.status, q.created_at, q.updated_at, "
                        + "q.stage_id, st.name AS stage_name, p.subject_id, s.name AS subject_name "
                        + "FROM question q JOIN stage st ON st.id = q.stage_id "
                        + "JOIN learning_path p ON p.id = st.path_id JOIN subject s ON s.id = p.subject_id "
                        + "WHERE q.id = ?", id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.QUESTION_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);

        List<QuestionDetail.OptionItem> options = jdbcTemplate.query(
                "SELECT id, option_key, content, is_correct, sort_no FROM question_option "
                        + "WHERE question_id = ? ORDER BY sort_no ASC, id ASC",
                (rs, rowNum) -> new QuestionDetail.OptionItem(
                        rs.getLong("id"),
                        rs.getString("option_key"),
                        rs.getString("content"),
                        rs.getBoolean("is_correct"),
                        rs.getInt("sort_no")),
                id);

        List<QuestionDetail.ScoringPointItem> points = jdbcTemplate.query(
                "SELECT id, point_text, sort_no FROM question_scoring_point "
                        + "WHERE question_id = ? ORDER BY sort_no ASC, id ASC",
                (rs, rowNum) -> new QuestionDetail.ScoringPointItem(
                        rs.getLong("id"),
                        rs.getString("point_text"),
                        rs.getInt("sort_no")),
                id);

        return new QuestionDetail(
                ((Number) row.get("id")).longValue(),
                dbToType(((Number) row.get("type")).intValue()),
                ((Number) row.get("subject_id")).longValue(),
                (String) row.get("subject_name"),
                ((Number) row.get("stage_id")).longValue(),
                (String) row.get("stage_name"),
                (String) row.get("content"),
                options,
                points,
                (String) row.get("analysis"),
                (String) row.get("reference_answer"),
                ((Number) row.get("status")).intValue() == STATUS_ENABLED ? "ENABLED" : "DISABLED",
                TimeFormats.rfc3339(row.get("created_at")),
                TimeFormats.rfc3339(row.get("updated_at")));
    }

    @Transactional
    public QuestionDetail create(CreateQuestionRequest request) {
        int type = typeToDb(request.type());
        String content = request.content().trim();
        requireStageBelongsToSubject(request.stageId(), request.subjectId());
        String hash = sha256(normalize(content));
        requireContentAvailable(request.stageId(), hash, null);

        List<CreateQuestionRequest.OptionInput> options =
                request.options() == null ? List.of() : request.options();
        List<CreateQuestionRequest.ScoringPointInput> points =
                request.scoringPoints() == null ? List.of() : request.scoringPoints();
        validateOptions(type, options);
        validateScoringPoints(type, points);

        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        String referenceAnswer = type == TYPE_SHORT_ANSWER ? trimToNull(request.referenceAnswer()) : null;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO question (stage_id, type, content, content_hash, analysis, reference_answer, "
                            + "status, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, request.stageId());
            statement.setInt(2, type);
            statement.setString(3, content);
            statement.setString(4, hash);
            statement.setString(5, request.analysis() == null ? "" : request.analysis().trim());
            statement.setString(6, referenceAnswer);
            statement.setLong(7, adminId);
            statement.setLong(8, adminId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long id = key == null ? 0 : key.longValue();

        insertOptions(id, options);
        insertScoringPoints(id, points);
        auditService.record("question", "create", "question", id, "新增题目 type=" + request.type());
        return detail(id);
    }

    @Transactional
    public QuestionDetail update(long id, UpdateQuestionRequest request) {
        QuestionDetail current = detail(id);
        int type = typeToDb(current.type());

        String newContent = request.content() == null ? current.content() : request.content().trim();
        if (newContent.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "题干不能为空");
        }
        long targetStageId = request.stageId() == null ? current.stageId() : request.stageId();
        if (request.stageId() != null) {
            requireStageBelongsToSubject(targetStageId, current.subjectId());
        }
        if (request.content() != null || request.stageId() != null) {
            requireContentAvailable(targetStageId, sha256(normalize(newContent)), id);
        }

        if (request.options() != null) {
            validateOptions(type, request.options());
        }
        if (request.scoringPoints() != null) {
            validateScoringPoints(type, request.scoringPoints());
        }

        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        jdbcTemplate.update(
                "UPDATE question SET content = ?, content_hash = ?, stage_id = ?, analysis = ?, "
                        + "reference_answer = ?, updated_by = ? WHERE id = ?",
                newContent,
                sha256(normalize(newContent)),
                targetStageId,
                request.analysis() == null ? current.analysis() : request.analysis().trim(),
                type == TYPE_SHORT_ANSWER
                        ? (request.referenceAnswer() == null ? current.referenceAnswer() : trimToNull(request.referenceAnswer()))
                        : null,
                adminId,
                id);

        if (request.options() != null) {
            jdbcTemplate.update("DELETE FROM question_option WHERE question_id = ?", id);
            insertOptions(id, request.options());
        }
        if (request.scoringPoints() != null) {
            jdbcTemplate.update("DELETE FROM question_scoring_point WHERE question_id = ?", id);
            insertScoringPoints(id, request.scoringPoints());
        }
        auditService.record("question", "update", "question", id, "编辑题目");
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

    private void changeStatus(long id, int target, ErrorCode alreadyCode, String action) {
        Integer current = jdbcTemplate.query("SELECT status FROM question WHERE id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, id);
        if (current == null) {
            throw new BizException(ErrorCode.QUESTION_NOT_FOUND);
        }
        if (current == target) {
            throw new BizException(alreadyCode);
        }
        jdbcTemplate.update("UPDATE question SET status = ?, updated_by = ? WHERE id = ?",
                target, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("question", action, "question", id,
                "题目状态变更为 " + (target == STATUS_ENABLED ? "ENABLED" : "DISABLED"));
    }

    private void insertOptions(long questionId, List<CreateQuestionRequest.OptionInput> options) {
        int index = 0;
        for (CreateQuestionRequest.OptionInput option : options) {
            String key = option.optionKey() == null || option.optionKey().isBlank()
                    ? String.valueOf((char) ('A' + index))
                    : option.optionKey().trim().toUpperCase();
            int sortNo = option.sortNo() == null ? index : option.sortNo();
            jdbcTemplate.update(
                    "INSERT INTO question_option (question_id, option_key, content, is_correct, sort_no) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    questionId, key, option.content().trim(), Boolean.TRUE.equals(option.isCorrect()) ? 1 : 0, sortNo);
            index++;
        }
    }

    private void insertScoringPoints(long questionId, List<CreateQuestionRequest.ScoringPointInput> points) {
        int index = 0;
        for (CreateQuestionRequest.ScoringPointInput point : points) {
            int sortNo = point.sortNo() == null ? index : point.sortNo();
            jdbcTemplate.update(
                    "INSERT INTO question_scoring_point (question_id, point_text, sort_no) VALUES (?, ?, ?)",
                    questionId, point.pointText().trim(), sortNo);
            index++;
        }
    }

    /** 04 §6.10.4.1：选项数量与正确答案个数按题型校验（40012） */
    private static void validateOptions(int type, List<CreateQuestionRequest.OptionInput> options) {
        long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.isCorrect())).count();
        switch (type) {
            case TYPE_TRUE_FALSE -> {
                if (options.size() != 2 || correctCount != 1) {
                    throw new BizException(ErrorCode.QUESTION_OPTION_INVALID);
                }
            }
            case TYPE_SINGLE -> {
                if (options.size() < 2 || options.size() > 8 || correctCount != 1) {
                    throw new BizException(ErrorCode.QUESTION_OPTION_INVALID);
                }
            }
            case TYPE_MULTIPLE -> {
                if (options.size() < 2 || options.size() > 8 || correctCount < 2) {
                    throw new BizException(ErrorCode.QUESTION_OPTION_INVALID);
                }
            }
            default -> {
                if (!options.isEmpty()) {
                    throw new BizException(ErrorCode.QUESTION_OPTION_INVALID);
                }
            }
        }
    }

    /** 简答题至少一条评分点（40013） */
    private static void validateScoringPoints(int type, List<CreateQuestionRequest.ScoringPointInput> points) {
        if (type == TYPE_SHORT_ANSWER && points.isEmpty()) {
            throw new BizException(ErrorCode.QUESTION_SCORING_POINT_REQUIRED);
        }
    }

    private void requireStageBelongsToSubject(long stageId, long subjectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT st.id, p.subject_id FROM stage st JOIN learning_path p ON p.id = st.path_id WHERE st.id = ?",
                stageId);
        if (rows.isEmpty() || ((Number) rows.get(0).get("subject_id")).longValue() != subjectId) {
            throw new BizException(ErrorCode.PARAM_INVALID, "阶段不存在或不属于所选学科");
        }
    }

    private void requireContentAvailable(long stageId, String hash, Long excludeId) {
        List<Long> rows = excludeId == null
                ? jdbcTemplate.queryForList(
                        "SELECT id FROM question WHERE stage_id = ? AND content_hash = ?", Long.class, stageId, hash)
                : jdbcTemplate.queryForList(
                        "SELECT id FROM question WHERE stage_id = ? AND content_hash = ? AND id <> ?",
                        Long.class, stageId, hash, excludeId);
        if (!rows.isEmpty()) {
            throw new BizException(ErrorCode.QUESTION_CONTENT_DUPLICATED);
        }
    }

    /** 题干归一化：导入与手工新增共用同一套去重口径 */
    public static String normalize(String content) {
        return content.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /** 题干哈希：导入与手工新增共用同一套去重口径 */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 题型字符串 → 数据库枚举值；非法题型直接 10001 */
    public static int typeToDb(String type) {
        return switch (type == null ? "" : type.trim().toUpperCase()) {
            case "TRUE_FALSE" -> TYPE_TRUE_FALSE;
            case "SINGLE_CHOICE" -> TYPE_SINGLE;
            case "MULTIPLE_CHOICE" -> TYPE_MULTIPLE;
            case "SHORT_ANSWER" -> TYPE_SHORT_ANSWER;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "题型只能是 TRUE_FALSE/SINGLE_CHOICE/MULTIPLE_CHOICE/SHORT_ANSWER");
        };
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

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
