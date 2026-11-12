package com.yunshu.zhixue.admin.student;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.admin.common.TempPassword;
import com.yunshu.zhixue.admin.student.dto.CancelStudentRequest;
import com.yunshu.zhixue.admin.student.dto.ResetPasswordResponse;
import com.yunshu.zhixue.admin.student.dto.StudentDetail;
import com.yunshu.zhixue.admin.student.dto.StudentListItem;
import com.yunshu.zhixue.admin.student.dto.UpdateCurrentPathRequest;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.auth.RefreshTokenService;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.security.JdbcAccountStatusVerifier;
import com.yunshu.zhixue.common.crypto.PhoneCrypto;
import com.yunshu.zhixue.common.util.MaskUtils;
import com.yunshu.zhixue.common.util.TimeFormats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学员管理（04 §6.10.7 / PRD 6.6）。
 *
 * <p>手机号只以 AES-GCM 密文 + HMAC 盲索引落库（03 §7.1），列表与详情一律返回脱敏值，
 * 精确搜索走 phone_hash；注销释放手机号（密文/索引/昵称置空），历史行为保留匿名关联。</p>
 */
@Service
public class AdminStudentService {

    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DISABLED = 2;
    private static final int STATUS_CANCELLED = 3;

    private static final int SUBJECT_TYPE_STUDENT = 1;
    private static final int REVOKE_PASSWORD_RESET = 2;
    private static final int REVOKE_DISABLED = 3;
    private static final int REVOKE_CANCELLED = 4;

    private static final int PRACTICE_STAGE = 1;
    private static final int PRACTICE_FINISHED = 2;
    private static final int INTERVIEW_COMPLETED = 3;

    private static final String CANCEL_CONFIRM_TEXT = "注销";

    private final JdbcTemplate jdbcTemplate;
    private final PhoneCrypto phoneCrypto;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AdminAuditService auditService;
    private final JdbcAccountStatusVerifier accountStatusVerifier;

    public AdminStudentService(JdbcTemplate jdbcTemplate, PhoneCrypto phoneCrypto,
                               PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                               AdminAuditService auditService, JdbcAccountStatusVerifier accountStatusVerifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.phoneCrypto = phoneCrypto;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.accountStatusVerifier = accountStatusVerifier;
    }

    // ------------------------------------------------------------------ 列表

    @Transactional(readOnly = true)
    public PageData<StudentListItem> list(int page, int size, String phone, String nickname,
                                          Long pathId, String status, String startDate, String endDate) {
        validatePaging(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();

        if (phone != null && !phone.isBlank()) {
            // 手机号只按盲索引精确匹配，不做模糊（04 §6.10.7）
            where.append(" AND s.phone_hash = ?");
            args.add(phoneCrypto.hmacHex(phone.trim()));
        }
        if (nickname != null && !nickname.isBlank()) {
            where.append(" AND s.nickname LIKE ?");
            args.add("%" + escapeLike(nickname.trim()) + "%");
        }
        if (pathId != null) {
            where.append(" AND s.current_path_id = ?");
            args.add(pathId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND s.status = ?");
            args.add(parseStatus(status));
        }
        if (startDate != null && !startDate.isBlank()) {
            where.append(" AND s.created_at >= ?");
            args.add(parseDate(startDate, "开始日期"));
        }
        if (endDate != null && !endDate.isBlank()) {
            where.append(" AND s.created_at < ?");
            args.add(parseDate(endDate, "结束日期").plusDays(1));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student s" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);

        List<StudentListItem> items = jdbcTemplate.query(
                "SELECT s.id, s.nickname, s.phone_cipher, s.status, s.last_login_at, s.created_at, "
                        + "p.name AS path_name FROM student s "
                        + "LEFT JOIN learning_path p ON p.id = s.current_path_id"
                        + where + " ORDER BY s.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new StudentListItem(
                        rs.getLong("id"),
                        displayNickname(rs.getString("nickname")),
                        maskPhone(rs.getBytes("phone_cipher")),
                        statusName(rs.getInt("status")),
                        rs.getString("path_name"),
                        TimeFormats.rfc3339(rs.getObject("last_login_at")),
                        TimeFormats.rfc3339(rs.getObject("created_at"))),
                pageArgs.toArray());
        return PageData.of(items, page, size, total == null ? 0 : total);
    }

    // ------------------------------------------------------------------ 详情

    @Transactional(readOnly = true)
    public StudentDetail detail(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.id, s.nickname, s.phone_cipher, s.avatar_url, s.status, s.created_at, "
                        + "s.last_login_at, s.cancelled_at, s.current_path_id, p.name AS path_name "
                        + "FROM student s LEFT JOIN learning_path p ON p.id = s.current_path_id WHERE s.id = ?", id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.STUDENT_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);
        Long currentPathId = nullableLong(row.get("current_path_id"));
        String pathName = (String) row.get("path_name");

        StudentDetail.Summary summary = new StudentDetail.Summary(
                ((Number) row.get("id")).longValue(),
                displayNickname((String) row.get("nickname")),
                maskPhone((byte[]) row.get("phone_cipher")),
                (String) row.get("avatar_url"),
                statusName(((Number) row.get("status")).intValue()),
                currentPathId == null ? null : new StudentDetail.PathRef(currentPathId, pathName),
                TimeFormats.rfc3339(row.get("created_at")),
                TimeFormats.rfc3339(row.get("last_login_at")),
                TimeFormats.rfc3339(row.get("cancelled_at")));

        return new StudentDetail(summary, progress(id, currentPathId), recentActivities(id),
                interviewSummary(id));
    }

    /** 完成度按 course_progress 与课程数实时算；掌握度取最近一次已完成阶段练习（03 §7.2.1） */
    private StudentDetail.Progress progress(long studentId, Long currentPathId) {
        int courseCount = 0;
        int completedCourses = 0;
        if (currentPathId != null) {
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM course c JOIN stage st ON st.id = c.stage_id "
                            + "WHERE st.path_id = ? AND c.status = 1", Integer.class, currentPathId);
            courseCount = total == null ? 0 : total;
            Integer done = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM course_progress cp JOIN course c ON c.id = cp.course_id "
                            + "JOIN stage st ON st.id = c.stage_id "
                            + "WHERE cp.student_id = ? AND st.path_id = ? AND cp.completed_at IS NOT NULL",
                    Integer.class, studentId, currentPathId);
            completedCourses = done == null ? 0 : done;
        }

        List<Map<String, Object>> mastery = jdbcTemplate.queryForList(
                "SELECT correct_rate, question_count, finished_at FROM practice "
                        + "WHERE student_id = ? AND type = ? AND status = ? "
                        + "ORDER BY finished_at DESC, id DESC LIMIT 1",
                studentId, PRACTICE_STAGE, PRACTICE_FINISHED);
        Double masteryRate = null;
        Integer masteryQuestionCount = null;
        String masteryAt = null;
        if (!mastery.isEmpty()) {
            Map<String, Object> m = mastery.get(0);
            Object rate = m.get("correct_rate");
            masteryRate = rate == null ? null : ((Number) rate).doubleValue();
            Object count = m.get("question_count");
            masteryQuestionCount = count == null ? null : ((Number) count).intValue();
            masteryAt = TimeFormats.rfc3339(m.get("finished_at"));
        }

        return new StudentDetail.Progress(completedCourses, courseCount, masteryRate,
                masteryQuestionCount, masteryAt, lastActiveAt(studentId));
    }

    /** 最近活跃取四张事实表的最大时间（03 §7.1 同口径），不建 student_activity 表 */
    private String lastActiveAt(long studentId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT GREATEST(COALESCE((SELECT last_login_at FROM student WHERE id = ?), '1970-01-01'), "
                        + "COALESCE((SELECT MAX(updated_at) FROM course_progress WHERE student_id = ?), '1970-01-01'), "
                        + "COALESCE((SELECT MAX(created_at) FROM practice WHERE student_id = ?), '1970-01-01'), "
                        + "COALESCE((SELECT MAX(created_at) FROM interview_session WHERE student_id = ?), '1970-01-01')"
                        + ") AS last_active_at",
                studentId, studentId, studentId, studentId);
        Object value = row.get("last_active_at");
        if (value == null) {
            return null;
        }
        String formatted = TimeFormats.rfc3339(value);
        return "1970-01-01T00:00:00Z".equals(formatted) ? null : formatted;
    }

    private List<StudentDetail.Activity> recentActivities(long studentId) {
        List<StudentDetail.Activity> activities = new ArrayList<>();

        jdbcTemplate.query(
                "SELECT c.title, cp.started_at, cp.completed_at FROM course_progress cp "
                        + "JOIN course c ON c.id = cp.course_id WHERE cp.student_id = ? "
                        + "ORDER BY cp.updated_at DESC LIMIT 5",
                rs -> {
                    String completedAt = TimeFormats.rfc3339(rs.getObject("completed_at"));
                    activities.add(new StudentDetail.Activity(
                            "COURSE",
                            rs.getString("title"),
                            completedAt == null ? "开始学习" : "已完成课程",
                            completedAt == null ? TimeFormats.rfc3339(rs.getObject("started_at")) : completedAt));
                }, studentId);

        jdbcTemplate.query(
                "SELECT p.type, p.status, p.correct_rate, p.started_at, p.finished_at, st.name AS stage_name "
                        + "FROM practice p LEFT JOIN stage st ON st.id = p.stage_id "
                        + "WHERE p.student_id = ? ORDER BY p.created_at DESC LIMIT 5",
                rs -> {
                    int status = rs.getInt("status");
                    Object rate = rs.getObject("correct_rate");
                    String title = rs.getInt("type") == PRACTICE_STAGE
                            ? (rs.getString("stage_name") == null ? "阶段练习" : rs.getString("stage_name") + " 阶段练习")
                            : "错题重练";
                    String detail = switch (status) {
                        case PRACTICE_FINISHED -> rate == null ? "已完成" : "正确率 " + ((Number) rate).doubleValue() + "%";
                        case 3 -> "已作废";
                        default -> "进行中";
                    };
                    String at = TimeFormats.rfc3339(rs.getObject("finished_at"));
                    activities.add(new StudentDetail.Activity("PRACTICE", title, detail,
                            at == null ? TimeFormats.rfc3339(rs.getObject("started_at")) : at));
                }, studentId);

        jdbcTemplate.query(
                "SELECT stage_name, status, created_at, finished_at FROM interview_session "
                        + "WHERE student_id = ? ORDER BY created_at DESC LIMIT 5",
                rs -> {
                    String detail = switch (rs.getInt("status")) {
                        case 2 -> "进行中";
                        case INTERVIEW_COMPLETED -> "正常完成";
                        case 4 -> "未完成";
                        case 5 -> "已过期";
                        case 6 -> "已作废";
                        default -> "创建中";
                    };
                    String at = TimeFormats.rfc3339(rs.getObject("finished_at"));
                    activities.add(new StudentDetail.Activity("INTERVIEW", rs.getString("stage_name") + " AI 面试",
                            detail, at == null ? TimeFormats.rfc3339(rs.getObject("created_at")) : at));
                }, studentId);

        activities.sort(Comparator.comparing(
                (StudentDetail.Activity a) -> a.at() == null ? "" : a.at()).reversed());
        return activities.size() > 10 ? activities.subList(0, 10) : activities;
    }

    private StudentDetail.InterviewSummary interviewSummary(long studentId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total, "
                        + "SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS completed, "
                        + "MAX(finished_at) AS last_finished_at FROM interview_session WHERE student_id = ?",
                INTERVIEW_COMPLETED, studentId);
        Number total = (Number) row.get("total");
        Number completed = (Number) row.get("completed");
        return new StudentDetail.InterviewSummary(
                total == null ? 0 : total.intValue(),
                completed == null ? 0 : completed.intValue(),
                TimeFormats.rfc3339(row.get("last_finished_at")));
    }

    // ------------------------------------------------------------------ 管理动作

    /** 重置密码：一次性明文只在本响应返回，并撤销全部 refresh token（04 §6.10.7） */
    @Transactional
    public ResetPasswordResponse resetPassword(long id) {
        Map<String, Object> student = requireStudent(id);
        requireNotCancelled(student);

        String temporaryPassword = TempPassword.generate();
        jdbcTemplate.update(
                "UPDATE student SET password_hash = ?, password_reset_required = 1 WHERE id = ?",
                passwordEncoder.encode(temporaryPassword), id);
        refreshTokenService.revokeAllFor(SUBJECT_TYPE_STUDENT, id, REVOKE_PASSWORD_RESET);
        auditService.record("student", "reset-password", "student", id, "重置学员密码，强制下次登录改密");
        return new ResetPasswordResponse(temporaryPassword, true,
                TimeFormats.rfc3339(LocalDateTime.now(ZoneOffset.UTC)));
    }

    /** 调整当前主路线：幂等；不删除历史进度，仅切主路线（04 §6.10.7） */
    @Transactional
    public Map<String, Object> updateCurrentPath(long id, UpdateCurrentPathRequest request) {
        Map<String, Object> student = requireStudent(id);
        requireNotCancelled(student);

        Long pathId = request.pathId();
        Long exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_path WHERE id = ?", Long.class, pathId);
        if (exists == null || exists == 0) {
            throw new BizException(ErrorCode.LEARNING_PATH_NOT_FOUND);
        }

        jdbcTemplate.update("UPDATE student SET current_path_id = ? WHERE id = ?", pathId, id);
        jdbcTemplate.update(
                "INSERT INTO student_path (student_id, path_id) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3)", id, pathId);

        String updatedAt = TimeFormats.rfc3339(LocalDateTime.now(ZoneOffset.UTC));
        auditService.record("student", "update", "student", id, "调整当前学习路线 pathId=" + pathId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentId", id);
        data.put("currentPathId", pathId);
        data.put("updatedAt", updatedAt);
        return data;
    }

    @Transactional
    public void disable(long id) {
        changeStatus(id, STATUS_DISABLED, ErrorCode.ALREADY_DISABLED, "disable", REVOKE_DISABLED);
    }

    @Transactional
    public void enable(long id) {
        changeStatus(id, STATUS_ACTIVE, ErrorCode.ALREADY_ENABLED, "enable", null);
    }

    /** 注销：二次确认后释放手机号、清空身份信息，历史行为保留匿名关联（04 §6.10.7） */
    @Transactional
    public Map<String, Object> cancel(long id, CancelStudentRequest request) {
        Map<String, Object> student = requireStudent(id);
        if (CANCEL_CONFIRM_TEXT.equals(request == null ? null : request.confirmText()) == false) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写确认文本「注销」");
        }
        if (((Number) student.get("status")).intValue() == STATUS_CANCELLED) {
            throw new BizException(ErrorCode.STUDENT_CANCELLED, "该学员已注销");
        }

        LocalDateTime cancelledAt = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "UPDATE student SET status = ?, cancelled_at = ?, phone_cipher = NULL, phone_hash = NULL, "
                        + "nickname = NULL, avatar_url = NULL, password_hash = ? WHERE id = ?",
                STATUS_CANCELLED, cancelledAt, passwordEncoder.encode(TempPassword.generate()), id);
        refreshTokenService.revokeAllFor(SUBJECT_TYPE_STUDENT, id, REVOKE_CANCELLED);
        accountStatusVerifier.evict(id, Audience.C);

        String reason = request.reason() == null ? "" : request.reason().trim();
        auditService.record("student", "cancel", "student", id,
                "注销学员" + (reason.isEmpty() ? "" : "，原因：" + reason));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("status", "CANCELLED");
        data.put("cancelledAt", TimeFormats.rfc3339(cancelledAt));
        return data;
    }

    private void changeStatus(long id, int target, ErrorCode alreadyCode, String action, Integer revokeReason) {
        Map<String, Object> student = requireStudent(id);
        requireNotCancelled(student);
        int current = ((Number) student.get("status")).intValue();
        if (current == target) {
            throw new BizException(alreadyCode);
        }
        jdbcTemplate.update("UPDATE student SET status = ? WHERE id = ?", target, id);
        if (revokeReason != null) {
            refreshTokenService.revokeAllFor(SUBJECT_TYPE_STUDENT, id, revokeReason);
        }
        // 停用/启用后立即生效，不等 60 秒状态缓存过期（02 §6.1）
        accountStatusVerifier.evict(id, Audience.C);
        auditService.record("student", action, "student", id,
                "学员状态变更为 " + statusName(target));
    }

    // ------------------------------------------------------------------ 内部

    private Map<String, Object> requireStudent(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status, nickname FROM student WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.STUDENT_NOT_FOUND);
        }
        return rows.get(0);
    }

    private static void requireNotCancelled(Map<String, Object> student) {
        if (((Number) student.get("status")).intValue() == STATUS_CANCELLED) {
            throw new BizException(ErrorCode.STUDENT_CANCELLED);
        }
    }

    private String maskPhone(byte[] cipher) {
        if (cipher == null || cipher.length == 0) {
            return null;
        }
        try {
            return MaskUtils.maskPhone(new String(phoneCrypto.decrypt(cipher), StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            // 历史脏数据或密钥变更时不影响列表可用性
            return null;
        }
    }

    private static String displayNickname(String nickname) {
        return nickname == null || nickname.isBlank() ? "已注销学员" : nickname;
    }

    private static String statusName(int status) {
        return switch (status) {
            case STATUS_ACTIVE -> "ACTIVE";
            case STATUS_DISABLED -> "DISABLED";
            case STATUS_CANCELLED -> "CANCELLED";
            default -> "UNKNOWN";
        };
    }

    private static int parseStatus(String status) {
        return switch (status.trim().toUpperCase()) {
            case "ACTIVE" -> STATUS_ACTIVE;
            case "DISABLED" -> STATUS_DISABLED;
            case "CANCELLED" -> STATUS_CANCELLED;
            default -> throw new BizException(ErrorCode.PARAM_INVALID,
                    "状态取值只能是 ACTIVE/DISABLED/CANCELLED");
        };
    }

    private static LocalDate parseDate(String value, String label) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "格式应为 yyyy-MM-dd");
        }
    }

    private static void validatePaging(int page, int size) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "页码必须大于等于 1");
        }
        if (size < 1 || size > PageQuery.MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "每页条数必须在 1-" + PageQuery.MAX_SIZE + " 之间");
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

}
