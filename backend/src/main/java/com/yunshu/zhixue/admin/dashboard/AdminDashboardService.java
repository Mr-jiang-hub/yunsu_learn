package com.yunshu.zhixue.admin.dashboard;

import com.yunshu.zhixue.admin.dashboard.dto.DashboardResponse;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 数据看板（04 §6.10.11 / PRD 6.10）。
 *
 * <p>口径（03 §7.2 / 05 Q35-Q36）：学员总数含注销；活跃学员取 last_login_at、
 * course_progress.started_at、practice.created_at、interview_session.created_at 四索引并集；
 * 正确率按题量加权且只用 type=1；面试完成率排除创建中与作废、含未完成/过期。
 * 自然日按 Asia/Shanghai 切分，落库时间为 UTC。</p>
 *
 * <p>每区块独立 try/catch：失败置 null 并写入 degradedSections；结果缓存 60 秒。</p>
 */
@Service
public class AdminDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardService.class);
    private static final int CACHE_TTL_SECONDS = 60;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int PRACTICE_STAGE = 1;
    private static final int PRACTICE_FINISHED = 2;
    private static final int INTERVIEW_TOTAL_DONE = 3;
    private static final int INTERVIEW_CREATING = 1;
    private static final int INTERVIEW_VOIDED = 6;

    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, DashboardResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(CACHE_TTL_SECONDS))
            .maximumSize(16)
            .build();

    public AdminDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public DashboardResponse load(String range) {
        String normalized = normalizeRange(range);
        return cache.get(normalized, key -> build(key));
    }

    private DashboardResponse build(String range) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        int days = switch (range) {
            case "TODAY" -> 1;
            case "7D" -> 7;
            default -> 30;
        };
        LocalDate startDate = today.minusDays(days - 1L);
        LocalDateTime startUtc = startDate.atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endUtc = today.plusDays(1).atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        Map<String, Object> sections = new LinkedHashMap<>();
        List<String> degraded = new ArrayList<>();

        sections.put("overview", section(degraded, "overview",
                () -> overview(startUtc, endUtc)));
        sections.put("learning", section(degraded, "learning",
                () -> learning(startUtc, endUtc)));
        sections.put("practice", section(degraded, "practice",
                () -> practice(startUtc, endUtc)));
        sections.put("interview", section(degraded, "interview",
                () -> interview(startUtc, endUtc)));
        sections.put("content", section(degraded, "content", this::content));
        sections.put("users", section(degraded, "users", this::users));

        return new DashboardResponse(range, startDate.toString(), today.toString(), sections,
                new DashboardResponse.Meta(TimeFormats.rfc3339(LocalDateTime.now(ZoneOffset.UTC)),
                        CACHE_TTL_SECONDS, degraded));
    }

    private Map<String, Object> overview(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentTotal", count("SELECT COUNT(*) FROM student"));
        data.put("activeStudentCount", count(
                "SELECT COUNT(DISTINCT id) FROM ("
                        + "SELECT id FROM student WHERE last_login_at >= ? AND last_login_at < ? "
                        + "UNION SELECT student_id FROM course_progress WHERE started_at >= ? AND started_at < ? "
                        + "UNION SELECT student_id FROM practice WHERE created_at >= ? AND created_at < ? "
                        + "UNION SELECT student_id FROM interview_session WHERE created_at >= ? AND created_at < ?"
                        + ") t", start, end, start, end, start, end, start, end));
        data.put("newStudentCount", count(
                "SELECT COUNT(*) FROM student WHERE created_at >= ? AND created_at < ?", start, end));
        return data;
    }

    private Map<String, Object> learning(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> data = new LinkedHashMap<>();
        int completed = count(
                "SELECT COUNT(*) FROM course_progress WHERE completed_at >= ? AND completed_at < ?", start, end);
        data.put("courseCompletionCount", completed);

        int studentTotal = count("SELECT COUNT(*) FROM student");
        int courseCount = count("SELECT COUNT(*) FROM course WHERE status = 1");
        long denominator = (long) studentTotal * courseCount;
        data.put("courseCompletionRate", rate(completed, denominator));

        // 阶段掌握度：最近一次已完成阶段练习（type=1）的正确率均值
        BigDecimal mastery = jdbcTemplate.queryForObject(
                "SELECT ROUND(AVG(last_rate), 2) FROM ("
                        + "SELECT p.correct_rate AS last_rate FROM practice p "
                        + "JOIN (SELECT student_id, stage_id, MAX(finished_at) AS last_at FROM practice "
                        + "      WHERE type = ? AND status = ? AND finished_at >= ? AND finished_at < ? "
                        + "      GROUP BY student_id, stage_id) latest "
                        + "  ON latest.student_id = p.student_id AND latest.last_at = p.finished_at "
                        + "WHERE p.type = ? AND p.status = ? AND p.correct_rate IS NOT NULL"
                        + ") t",
                BigDecimal.class, PRACTICE_STAGE, PRACTICE_FINISHED, start, end,
                PRACTICE_STAGE, PRACTICE_FINISHED);
        data.put("stageMasteryRate", mastery);
        return data;
    }

    private Map<String, Object> practice(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> data = new LinkedHashMap<>();
        int total = count("SELECT COUNT(*) FROM practice WHERE created_at >= ? AND created_at < ?", start, end);
        int finished = count("SELECT COUNT(*) FROM practice WHERE created_at >= ? AND created_at < ? "
                + "AND status = ?", start, end, PRACTICE_FINISHED);
        data.put("practiceCount", total);
        data.put("practiceCompletionRate", rate(finished, total));

        // 正确率按题量加权，只用阶段练习（type=1）
        BigDecimal weighted = jdbcTemplate.queryForObject(
                "SELECT ROUND(SUM(correct_rate * question_count) / NULLIF(SUM(question_count), 0), 2) "
                        + "FROM practice WHERE type = ? AND status = ? AND created_at >= ? AND created_at < ? "
                        + "AND correct_rate IS NOT NULL AND question_count > 0",
                BigDecimal.class, PRACTICE_STAGE, PRACTICE_FINISHED, start, end);
        data.put("averageCorrectRate", weighted);
        return data;
    }

    private Map<String, Object> interview(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> data = new LinkedHashMap<>();
        int total = count("SELECT COUNT(*) FROM interview_session WHERE created_at >= ? AND created_at < ?",
                start, end);
        int completed = count("SELECT COUNT(*) FROM interview_session WHERE created_at >= ? AND created_at < ? "
                + "AND status = ?", start, end, INTERVIEW_TOTAL_DONE);
        int valid = count("SELECT COUNT(*) FROM interview_session WHERE created_at >= ? AND created_at < ? "
                + "AND status NOT IN (?, ?)", start, end, INTERVIEW_CREATING, INTERVIEW_VOIDED);
        data.put("sessionCount", total);
        data.put("completionRate", rate(completed, valid));

        BigDecimal avgScore = jdbcTemplate.queryForObject(
                "SELECT ROUND(AVG(r.total_score), 2) FROM interview_report r "
                        + "JOIN interview_session s ON s.id = r.session_id "
                        + "WHERE s.created_at >= ? AND s.created_at < ? AND r.status = 1",
                BigDecimal.class, start, end);
        data.put("averageScore", avgScore);
        return data;
    }

    private Map<String, Object> content() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectCount", count("SELECT COUNT(*) FROM subject WHERE status = 1"));
        data.put("courseCount", count("SELECT COUNT(*) FROM course WHERE status = 1"));
        data.put("questionCount", count("SELECT COUNT(*) FROM question WHERE status = 1"));
        return data;
    }

    private Map<String, Object> users() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("adminUserCount", count("SELECT COUNT(*) FROM admin_user"));
        data.put("activeAdminCount", count("SELECT COUNT(*) FROM admin_user WHERE status = 1"));
        return data;
    }

    // ------------------------------------------------------------------ 工具

    private Object section(List<String> degraded, String name, Supplier<Map<String, Object>> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            log.error("看板区块取数失败 section={}", name, ex);
            degraded.add(name);
            return null;
        }
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static BigDecimal rate(int numerator, int denominator) {
        return rate((long) numerator, (long) denominator);
    }

    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "7D";
        }
        String value = range.trim().toUpperCase();
        return switch (value) {
            case "TODAY", "7D", "30D" -> value;
            default -> throw new BizException(ErrorCode.DASHBOARD_RANGE_INVALID);
        };
    }
}
