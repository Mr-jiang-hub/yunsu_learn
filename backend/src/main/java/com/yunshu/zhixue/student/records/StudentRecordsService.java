package com.yunshu.zhixue.student.records;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.student.records.dto.CourseRecordItem;
import com.yunshu.zhixue.student.records.dto.InterviewCompareResponse;
import com.yunshu.zhixue.student.records.dto.MasteryTrendResponse;
import com.yunshu.zhixue.student.records.dto.PracticeRecordItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * C 端学习与面试记录（04 §6.8）。
 *
 * <p>口径约束：</p>
 * <ol>
 *   <li>课程记录只返回 `COMPLETED`（有 `completed_at`），按完成时间倒序；</li>
 *   <li>练习记录包含阶段练习与错题重练，错题重练 `masteryEligible=false`；</li>
 *   <li>掌握度趋势只取阶段练习，错题重练不进入趋势；</li>
 *   <li>面试对比只允许同一学科、两份 `NORMAL` 报告，否则 `30052`。</li>
 * </ol>
 */
@Service
public class StudentRecordsService {

    private static final Logger log = LoggerFactory.getLogger(StudentRecordsService.class);

    private static final int PRACTICE_STAGE = 1;
    private static final int PRACTICE_WRONG_RETRY = 2;

    private static final int PRACTICE_IN_PROGRESS = 1;
    private static final int PRACTICE_FINISHED = 2;
    private static final int PRACTICE_VOIDED = 3;

    private static final int REPORT_NORMAL = 1;

    private static final int TREND_DEFAULT_LIMIT = 20;
    private static final int TREND_MAX_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;

    public StudentRecordsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ------------------------------------------------------------------ 课程记录

    /** 已完成课程（04 §6.8）：`subjectId`/`stageId` 为可选筛选 */
    public PageData<CourseRecordItem> courses(long studentId, PageQuery pageQuery, Long subjectId, Long stageId) {
        StringBuilder from = new StringBuilder(
                "FROM course_progress cp "
                        + "JOIN course c ON c.id = cp.course_id "
                        + "LEFT JOIN stage st ON st.id = c.stage_id "
                        + "LEFT JOIN learning_path p ON p.id = st.path_id "
                        + "LEFT JOIN subject s ON s.id = p.subject_id "
                        + "WHERE cp.student_id = ? AND cp.completed_at IS NOT NULL");
        List<Object> params = new ArrayList<>();
        params.add(studentId);
        if (subjectId != null) {
            from.append(" AND s.id = ?");
            params.add(subjectId);
        }
        if (stageId != null) {
            from.append(" AND st.id = ?");
            params.add(stageId);
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + from, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageQuery.getSize());
        pageParams.add(pageQuery.offset());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT cp.course_id, c.title, s.name AS subject_name, st.name AS stage_name, "
                        + "cp.started_at, cp.completed_at " + from
                        + " ORDER BY cp.completed_at DESC, cp.course_id DESC LIMIT ? OFFSET ?",
                pageParams.toArray());

        List<CourseRecordItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(new CourseRecordItem(
                    number(row.get("course_id")),
                    (String) row.get("title"),
                    (String) row.get("subject_name"),
                    (String) row.get("stage_name"),
                    TimeFormats.rfc3339(row.get("started_at")),
                    TimeFormats.rfc3339(row.get("completed_at"))));
        }
        return PageData.of(items, pageQuery.getPage(), pageQuery.getSize(), total == null ? 0L : total);
    }

    // ------------------------------------------------------------------ 练习记录

    /** 练习记录（04 §6.8）：包含阶段练习与错题重练，`masteryEligible` 区分是否计入掌握度 */
    public PageData<PracticeRecordItem> practices(long studentId, PageQuery pageQuery, String type, String status,
                                                  Long subjectId) {
        StringBuilder from = new StringBuilder(
                "FROM practice p "
                        + "LEFT JOIN stage st ON st.id = p.stage_id "
                        + "LEFT JOIN learning_path pa ON pa.id = st.path_id "
                        + "WHERE p.student_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(studentId);
        if (type != null && !type.isBlank()) {
            from.append(" AND p.type = ?");
            params.add(practiceTypeCode(type));
        }
        if (status != null && !status.isBlank()) {
            from.append(" AND p.status = ?");
            params.add(practiceStatusCode(status));
        }
        if (subjectId != null) {
            from.append(" AND pa.subject_id = ?");
            params.add(subjectId);
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + from, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageQuery.getSize());
        pageParams.add(pageQuery.offset());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.id, p.type, p.status, st.name AS stage_name, p.question_count, p.correct_count, "
                        + "p.correct_rate, p.started_at, p.finished_at " + from
                        + " ORDER BY p.started_at DESC, p.id DESC LIMIT ? OFFSET ?",
                pageParams.toArray());

        List<PracticeRecordItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            int practiceType = intValue(row.get("type"));
            boolean masteryEligible = practiceType == PRACTICE_STAGE;
            double correctRate = row.get("correct_rate") == null
                    ? 0d : ((Number) row.get("correct_rate")).doubleValue();
            items.add(new PracticeRecordItem(
                    number(row.get("id")),
                    masteryEligible ? "STAGE" : "WRONG_RETRY",
                    statusText(intValue(row.get("status"))),
                    (String) row.get("stage_name"),
                    intValue(row.get("question_count")),
                    intValue(row.get("correct_count")),
                    correctRate,
                    masteryEligible ? correctRate : null,
                    masteryEligible,
                    TimeFormats.rfc3339(row.get("started_at")),
                    TimeFormats.rfc3339(row.get("finished_at"))));
        }
        return PageData.of(items, pageQuery.getPage(), pageQuery.getSize(), total == null ? 0L : total);
    }

    // ------------------------------------------------------------------ 掌握度趋势

    /** 掌握度趋势（04 §6.8）：`stageId` 必填，`limit` 默认 20、上限 50，按完成时间升序 */
    public MasteryTrendResponse masteryTrend(long studentId, long stageId, Integer limit) {
        List<Map<String, Object>> stages = jdbcTemplate.queryForList(
                "SELECT st.id, st.name FROM stage st WHERE st.id = ? AND st.status = 1", stageId);
        if (stages.isEmpty()) {
            throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "阶段不存在或已停用");
        }
        int size = limit == null ? TREND_DEFAULT_LIMIT : Math.max(1, Math.min(TREND_MAX_LIMIT, limit));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, correct_rate, finished_at FROM practice "
                        + "WHERE student_id = ? AND stage_id = ? AND type = ? AND status = ? "
                        + "ORDER BY finished_at DESC, id DESC LIMIT ?",
                studentId, stageId, PRACTICE_STAGE, PRACTICE_FINISHED, size);
        // 取最近 N 条后反转为升序，保证折线从左到右按时间推进
        List<MasteryTrendResponse.Point> points = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            Map<String, Object> row = rows.get(i);
            double rate = row.get("correct_rate") == null
                    ? 0d : ((Number) row.get("correct_rate")).doubleValue();
            points.add(new MasteryTrendResponse.Point(
                    number(row.get("id")), TimeFormats.rfc3339(row.get("finished_at")), rate, rate));
        }
        return new MasteryTrendResponse(stageId, (String) stages.get(0).get("name"), points);
    }

    // ------------------------------------------------------------------ 面试对比

    /** 两次面试对比（04 §6.8）：同一学科、两份 `NORMAL` 报告，否则 `30052` */
    public InterviewCompareResponse compare(long studentId, long baseSessionId, long targetSessionId) {
        if (baseSessionId == targetSessionId) {
            throw new BizException(ErrorCode.PARAM_INVALID, "两次对比必须是不同的面试");
        }
        SessionSide base = loadSide(baseSessionId, studentId);
        SessionSide target = loadSide(targetSessionId, studentId);
        if (base.subjectId() != target.subjectId()) {
            throw new BizException(ErrorCode.INTERVIEW_REPORT_ABSENT, "仅支持同一学科的两次面试对比");
        }

        Map<Long, ReportDimension> baseDimensions = loadDimensions(base.reportId());
        Map<Long, ReportDimension> targetDimensions = loadDimensions(target.reportId());

        Map<Long, InterviewCompareResponse.DimensionDelta> merged = new LinkedHashMap<>();
        for (ReportDimension dimension : baseDimensions.values()) {
            Double targetScore = targetDimensions.containsKey(dimension.dimensionId())
                    ? targetDimensions.get(dimension.dimensionId()).score() : null;
            merged.put(dimension.dimensionId(), new InterviewCompareResponse.DimensionDelta(
                    dimension.dimensionId(), dimension.dimensionName(), dimension.score(), targetScore,
                    targetScore == null ? null : round2(targetScore - dimension.score())));
        }
        for (ReportDimension dimension : targetDimensions.values()) {
            if (!merged.containsKey(dimension.dimensionId())) {
                merged.put(dimension.dimensionId(), new InterviewCompareResponse.DimensionDelta(
                        dimension.dimensionId(), dimension.dimensionName(), null, dimension.score(), null));
            }
        }

        Double totalDelta = base.totalScore() == null || target.totalScore() == null
                ? null : round2(target.totalScore() - base.totalScore());
        log.debug("面试对比: studentId={}, base={}, target={}, totalDelta={}",
                studentId, baseSessionId, targetSessionId, totalDelta);

        return new InterviewCompareResponse(
                new InterviewCompareResponse.Side(base.sessionId(), base.subjectName(),
                        base.finishedAt(), base.totalScore()),
                new InterviewCompareResponse.Side(target.sessionId(), target.subjectName(),
                        target.finishedAt(), target.totalScore()),
                new ArrayList<>(merged.values()),
                totalDelta);
    }

    private SessionSide loadSide(long sessionId, long studentId) {
        List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                "SELECT s.id, s.subject_id, sb.name AS subject_name, s.finished_at "
                        + "FROM interview_session s LEFT JOIN subject sb ON sb.id = s.subject_id "
                        + "WHERE s.id = ? AND s.student_id = ?", sessionId, studentId);
        if (sessions.isEmpty()) {
            throw new BizException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        Map<String, Object> session = sessions.get(0);

        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                "SELECT id, total_score, status FROM interview_report WHERE session_id = ?", sessionId);
        if (reports.isEmpty() || intValue(reports.get(0).get("status")) != REPORT_NORMAL) {
            throw new BizException(ErrorCode.INTERVIEW_REPORT_ABSENT, "两份对比都必须有正常面试报告");
        }
        Map<String, Object> report = reports.get(0);
        return new SessionSide(
                number(session.get("id")),
                number(session.get("subject_id")),
                (String) session.get("subject_name"),
                TimeFormats.rfc3339(session.get("finished_at")),
                number(report.get("id")),
                ((Number) report.get("total_score")).doubleValue());
    }

    private Map<Long, ReportDimension> loadDimensions(long reportId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT dimension_id, dimension_name, score FROM report_dimension "
                        + "WHERE report_id = ? ORDER BY id ASC", reportId);
        Map<Long, ReportDimension> dimensions = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long dimensionId = number(row.get("dimension_id"));
            dimensions.put(dimensionId, new ReportDimension(
                    dimensionId, (String) row.get("dimension_name"),
                    ((Number) row.get("score")).doubleValue()));
        }
        return dimensions;
    }

    // ------------------------------------------------------------------ 枚举与工具

    private static int practiceTypeCode(String type) {
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "STAGE" -> PRACTICE_STAGE;
            case "WRONG_RETRY" -> PRACTICE_WRONG_RETRY;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "type 取值不合法");
        };
    }

    private static int practiceStatusCode(String status) {
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "IN_PROGRESS" -> PRACTICE_IN_PROGRESS;
            case "FINISHED" -> PRACTICE_FINISHED;
            case "VOIDED" -> PRACTICE_VOIDED;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "status 取值不合法");
        };
    }

    private static String statusText(int status) {
        return switch (status) {
            case PRACTICE_IN_PROGRESS -> "IN_PROGRESS";
            case PRACTICE_FINISHED -> "FINISHED";
            case PRACTICE_VOIDED -> "VOIDED";
            default -> "UNKNOWN";
        };
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private static long number(Object value) {
        return value instanceof Number num ? num.longValue() : 0L;
    }

    /** TINYINT(1) 会被 JDBC 映射成 Boolean，必须同时兼容，否则 1/0 会被读成 0 */
    private static int intValue(Object value) {
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return 0;
    }

    private record SessionSide(
            long sessionId,
            long subjectId,
            String subjectName,
            String finishedAt,
            long reportId,
            Double totalScore
    ) {
    }

    private record ReportDimension(long dimensionId, String dimensionName, double score) {
    }
}
