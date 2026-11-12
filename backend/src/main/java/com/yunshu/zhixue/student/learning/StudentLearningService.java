package com.yunshu.zhixue.student.learning;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.storage.CoverUrlResolver;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.student.learning.dto.CourseProgressResponse;
import com.yunshu.zhixue.student.learning.dto.CurrentPathResponse;
import com.yunshu.zhixue.student.learning.dto.LearningPathCard;
import com.yunshu.zhixue.student.learning.dto.LearningPathListResponse;
import com.yunshu.zhixue.student.learning.dto.PathDetailResponse;
import com.yunshu.zhixue.student.learning.dto.PathStageItem;
import com.yunshu.zhixue.student.learning.dto.StageCourseItem;
import com.yunshu.zhixue.student.learning.dto.StageDetailResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * C 端学习路线与课程进度（04 §6.3 / §6.4）。
 *
 * <p>完成度与掌握度均实时计算，不落冗余列（03 §7.1.3）：完成度 = 已完成的启用课程数 / 启用课程数；
 * 掌握度 = 该阶段最近一次已完成阶段练习（`type=1`、`status=2`）的 `correct_rate`，
 * 无可比样本时返回 0 并由 `hasPracticeSample=false` 让前端标注"样本不足"。</p>
 */
@Service
public class StudentLearningService {

    private static final int STATUS_ENABLED = 1;

    private static final String SELECT_PATH_BASE =
            "SELECT p.id, p.name, p.description, p.subject_id, s.name AS subject_name, "
                    + "(SELECT COUNT(*) FROM stage st WHERE st.path_id = p.id AND st.status = 1) AS stage_count, "
                    + "(SELECT COUNT(*) FROM course c JOIN stage st ON st.id = c.stage_id "
                    + " WHERE st.path_id = p.id AND st.status = 1 AND c.status = 1) AS course_count, "
                    + "(SELECT COUNT(*) FROM question q JOIN stage st ON st.id = q.stage_id "
                    + " WHERE st.path_id = p.id AND st.status = 1 AND q.status = 1) AS question_count, "
                    + "(SELECT COUNT(*) FROM course_progress cp JOIN course c ON c.id = cp.course_id "
                    + " JOIN stage st ON st.id = c.stage_id WHERE st.path_id = p.id AND st.status = 1 "
                    + " AND c.status = 1 AND cp.student_id = ? AND cp.completed_at IS NOT NULL) AS completed_count "
                    + "FROM learning_path p JOIN subject s ON s.id = p.subject_id ";

    private static final String SELECT_STAGE_MASTERY =
            "SELECT AVG(x.rate) FROM ("
                    + " SELECT (SELECT pr.correct_rate FROM practice pr WHERE pr.student_id = ? "
                    + "  AND pr.stage_id = st.id AND pr.type = 1 AND pr.status = 2 "
                    + "  ORDER BY pr.finished_at DESC, pr.id DESC LIMIT 1) AS rate"
                    + " FROM stage st WHERE st.path_id = ? AND st.status = 1) x WHERE x.rate IS NOT NULL";

    private final JdbcTemplate jdbcTemplate;
    private final CoverUrlResolver coverUrlResolver;

    public StudentLearningService(JdbcTemplate jdbcTemplate, CoverUrlResolver coverUrlResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.coverUrlResolver = coverUrlResolver;
    }

    // ------------------------------------------------------------------ 路线

    /**
     * 路线掌握度：各启用阶段"最近一次阶段练习"正确率的平均值，无样本返回 null。
     * 首页聚合（04 §6.2）与路线卡片共用同一口径。
     */
    @Transactional(readOnly = true)
    public Double pathMasteryRate(long studentId, long pathId) {
        return jdbcTemplate.queryForObject(SELECT_STAGE_MASTERY, Double.class, studentId, pathId);
    }

    /** 只返回启用学科下的启用路线；无路线返回空数组，不是错误（04 §6.3） */
    @Transactional(readOnly = true)
    public LearningPathListResponse listPaths(long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_PATH_BASE + "WHERE p.status = 1 AND s.status = 1 ORDER BY s.sort_no ASC, p.id ASC",
                studentId);
        Long currentPathId = currentPathId(studentId);

        List<LearningPathCard> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            long pathId = number(row.get("id"));
            int courseCount = intValue(row.get("course_count"));
            int completed = intValue(row.get("completed_count"));
            Double mastery = jdbcTemplate.queryForObject(
                    SELECT_STAGE_MASTERY, Double.class, studentId, pathId);
            items.add(new LearningPathCard(
                    pathId,
                    String.valueOf(row.get("name")),
                    number(row.get("subject_id")),
                    String.valueOf(row.get("subject_name")),
                    intValue(row.get("stage_count")),
                    courseCount,
                    intValue(row.get("question_count")),
                    rate(completed, courseCount),
                    round2(mastery),
                    currentPathId != null && currentPathId == pathId));
        }
        return new LearningPathListResponse(items, currentPathId);
    }

    @Transactional(readOnly = true)
    public PathDetailResponse pathDetail(long pathId, long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_PATH_BASE + "WHERE p.id = ? AND p.status = 1 AND s.status = 1",
                studentId, pathId);
        if (rows.isEmpty()) {
            requirePathVisible(pathId);
        }
        Map<String, Object> row = rows.get(0);
        int courseCount = intValue(row.get("course_count"));
        int completed = intValue(row.get("completed_count"));
        Double mastery = jdbcTemplate.queryForObject(
                SELECT_STAGE_MASTERY, Double.class, studentId, pathId);
        Long currentPathId = currentPathId(studentId);

        PathDetailResponse.PathBrief brief = new PathDetailResponse.PathBrief(
                pathId,
                String.valueOf(row.get("name")),
                number(row.get("subject_id")),
                String.valueOf(row.get("subject_name")),
                emptyIfNull(row.get("description")),
                intValue(row.get("stage_count")),
                courseCount,
                intValue(row.get("question_count")),
                rate(completed, courseCount),
                round2(mastery),
                currentPathId != null && currentPathId == pathId);

        List<PathStageItem> stages = stagesOfPath(pathId, studentId);
        Long currentStageId = null;
        for (PathStageItem stage : stages) {
            if (!"ENABLED".equals(stage.status())) {
                continue;
            }
            if (stage.completedCourseCount() < stage.courseCount()) {
                currentStageId = stage.stageId();
                break;
            }
            currentStageId = stage.stageId();
        }
        return new PathDetailResponse(brief, currentStageId, stages);
    }

    /** 切换主路线：幂等；不删除历史进度，只改当前指针并补记加入历史（04 §6.3） */
    @Transactional
    public CurrentPathResponse updateCurrentPath(long studentId, long pathId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.id, p.name, p.status, s.status AS subject_status "
                        + "FROM learning_path p JOIN subject s ON s.id = p.subject_id WHERE p.id = ?",
                pathId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND, "学习路线不存在或无权访问");
        }
        Map<String, Object> row = rows.get(0);
        if (intValue(row.get("status")) != STATUS_ENABLED || intValue(row.get("subject_status")) != STATUS_ENABLED) {
            throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "该路线暂不可学");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("UPDATE student SET current_path_id = ? WHERE id = ?", pathId, studentId);
        jdbcTemplate.update(
                "INSERT INTO student_path (student_id, path_id, joined_at) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE path_id = path_id",
                studentId, pathId, now);

        return new CurrentPathResponse(pathId, String.valueOf(row.get("name")), TimeFormats.rfc3339(now));
    }

    // ------------------------------------------------------------------ 阶段与课程

    @Transactional(readOnly = true)
    public StageDetailResponse stageDetail(long stageId, long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT st.id, st.name, st.path_id, st.sort_no, st.status, "
                        + "p.name AS path_name, p.status AS path_status, s.status AS subject_status "
                        + "FROM stage st JOIN learning_path p ON p.id = st.path_id "
                        + "JOIN subject s ON s.id = p.subject_id WHERE st.id = ?",
                stageId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND, "阶段不存在或无权访问");
        }
        Map<String, Object> row = rows.get(0);
        boolean stageEnabled = intValue(row.get("status")) == STATUS_ENABLED;
        boolean pathEnabled = intValue(row.get("path_status")) == STATUS_ENABLED
                && intValue(row.get("subject_status")) == STATUS_ENABLED;
        if (!stageEnabled || !pathEnabled) {
            throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "该阶段暂不可学");
        }

        List<StageCourseItem> courses = coursesOfStage(stageId, studentId);
        int courseCount = 0;
        int completedCourseCount = 0;
        for (StageCourseItem course : courses) {
            if (!"DISABLED".equals(course.status())) {
                courseCount++;
                if ("COMPLETED".equals(course.progressStatus())) {
                    completedCourseCount++;
                }
            }
        }
        // 无已完成练习时该查询返回 0 行，queryForObject 会抛 EmptyResult，故用列表取值
        List<Double> masteryRows = jdbcTemplate.queryForList(
                "SELECT pr.correct_rate FROM practice pr WHERE pr.student_id = ? AND pr.stage_id = ? "
                        + "AND pr.type = 1 AND pr.status = 2 ORDER BY pr.finished_at DESC, pr.id DESC LIMIT 1",
                Double.class, studentId, stageId);
        Double mastery = masteryRows.isEmpty() ? null : masteryRows.get(0);
        int questionCount = intValue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question WHERE stage_id = ? AND status = 1", Integer.class, stageId));

        StageDetailResponse.PracticeAvailability practice = questionCount > 0
                ? new StageDetailResponse.PracticeAvailability(true, questionCount, "")
                : new StageDetailResponse.PracticeAvailability(false, 0, "该阶段暂无可用题目");

        return new StageDetailResponse(
                new StageDetailResponse.StageBrief(
                        stageId,
                        String.valueOf(row.get("name")),
                        number(row.get("path_id")),
                        String.valueOf(row.get("path_name")),
                        intValue(row.get("sort_no")),
                        "ENABLED"),
                courses,
                new StageDetailResponse.Progress(
                        completedCourseCount,
                        courseCount,
                        rate(completedCourseCount, courseCount),
                        round2(mastery),
                        mastery != null),
                practice);
    }

    /** 开始学习：唯一键去重，重复调用返回首次 startedAt（04 §6.4） */
    @Transactional
    public CourseProgressResponse startCourse(long courseId, long studentId) {
        Map<String, Object> course = requireCourse(courseId);
        boolean hasProgress = hasProgress(studentId, courseId);
        if (intValue(course.get("status")) != STATUS_ENABLED && !hasProgress) {
            throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "该课程暂不可学习");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO course_progress (student_id, course_id, started_at) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE course_id = course_id",
                studentId, courseId, now);
        return progressOf(courseId, studentId, emptyIfNull(course.get("bilibili_url")));
    }

    /** 标记完成：重复调用返回首次 completedAt；未 start 时补建进度并直接完成（04 §6.4） */
    @Transactional
    public CourseProgressResponse completeCourse(long courseId, long studentId) {
        Map<String, Object> course = requireCourse(courseId);
        boolean hasProgress = hasProgress(studentId, courseId);
        if (intValue(course.get("status")) != STATUS_ENABLED && !hasProgress) {
            throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "该课程暂不可学习");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO course_progress (student_id, course_id, started_at, completed_at) "
                        + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                        + "completed_at = COALESCE(completed_at, VALUES(completed_at))",
                studentId, courseId, now, now);
        return progressOf(courseId, studentId, emptyIfNull(course.get("bilibili_url")));
    }

    // ------------------------------------------------------------------ 内部

    private List<PathStageItem> stagesOfPath(long pathId, long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT st.id, st.name, st.sort_no, st.status, "
                        + "(SELECT COUNT(*) FROM course c WHERE c.stage_id = st.id AND c.status = 1) AS course_count, "
                        + "(SELECT COUNT(*) FROM course_progress cp JOIN course c ON c.id = cp.course_id "
                        + " WHERE c.stage_id = st.id AND c.status = 1 AND cp.student_id = ? "
                        + " AND cp.completed_at IS NOT NULL) AS completed_count, "
                        + "(SELECT COUNT(*) FROM question q WHERE q.stage_id = st.id AND q.status = 1) AS question_count, "
                        + "(SELECT pr.correct_rate FROM practice pr WHERE pr.student_id = ? AND pr.stage_id = st.id "
                        + " AND pr.type = 1 AND pr.status = 2 ORDER BY pr.finished_at DESC, pr.id DESC LIMIT 1) AS mastery "
                        + "FROM stage st WHERE st.path_id = ? ORDER BY st.sort_no ASC, st.id ASC",
                studentId, studentId, pathId);
        List<PathStageItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object mastery = row.get("mastery");
            items.add(new PathStageItem(
                    number(row.get("id")),
                    String.valueOf(row.get("name")),
                    intValue(row.get("sort_no")),
                    intValue(row.get("course_count")),
                    intValue(row.get("completed_count")),
                    intValue(row.get("question_count")),
                    mastery == null ? 0d : round2(((Number) mastery).doubleValue()),
                    mastery != null,
                    intValue(row.get("status")) == STATUS_ENABLED ? "ENABLED" : "DISABLED"));
        }
        return items;
    }

    private List<StageCourseItem> coursesOfStage(long stageId, long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.id, c.title, c.bilibili_url, c.cover_url, c.duration_seconds, c.sort_no, c.status, "
                        + "cp.started_at, cp.completed_at FROM course c "
                        + "LEFT JOIN course_progress cp ON cp.course_id = c.id AND cp.student_id = ? "
                        + "WHERE c.stage_id = ? AND (c.status = 1 OR cp.id IS NOT NULL) "
                        + "ORDER BY c.sort_no ASC, c.id ASC",
                studentId, stageId);
        List<StageCourseItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object completedAt = row.get("completed_at");
            Object startedAt = row.get("started_at");
            String progressStatus = completedAt != null ? "COMPLETED"
                    : (startedAt != null ? "IN_PROGRESS" : "NOT_STARTED");
            items.add(new StageCourseItem(
                    number(row.get("id")),
                    String.valueOf(row.get("title")),
                    emptyIfNull(row.get("bilibili_url")),
                    coverUrlResolver.resolve(emptyIfNull(row.get("cover_url"))),
                    row.get("duration_seconds") == null ? null : intValue(row.get("duration_seconds")),
                    intValue(row.get("sort_no")),
                    progressStatus,
                    TimeFormats.rfc3339(startedAt),
                    TimeFormats.rfc3339(completedAt),
                    intValue(row.get("status")) == STATUS_ENABLED ? "ENABLED" : "DISABLED"));
        }
        return items;
    }

    /** 路线存在但被停用 → 30031；完全不存在 → 30020（不存在/无权，403） */
    private void requirePathVisible(long pathId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_path WHERE id = ?", Integer.class, pathId);
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND, "学习路线不存在或无权访问");
        }
        throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "该路线暂不可学");
    }

    private Long currentPathId(long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT current_path_id FROM student WHERE id = ?", studentId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        Object value = rows.get(0).get("current_path_id");
        return value == null ? null : ((Number) value).longValue();
    }

    private Map<String, Object> requireCourse(long courseId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status, bilibili_url FROM course WHERE id = ?", courseId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "课程不存在或不可学习");
        }
        return rows.get(0);
    }

    private boolean hasProgress(long studentId, long courseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_progress WHERE student_id = ? AND course_id = ?",
                Integer.class, studentId, courseId);
        return count != null && count > 0;
    }

    private CourseProgressResponse progressOf(long courseId, long studentId, String bilibiliUrl) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT started_at, completed_at FROM course_progress WHERE student_id = ? AND course_id = ?",
                studentId, courseId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "课程进度不存在");
        }
        Map<String, Object> row = rows.get(0);
        Object completedAt = row.get("completed_at");
        return new CourseProgressResponse(
                courseId,
                bilibiliUrl,
                completedAt == null ? "IN_PROGRESS" : "COMPLETED",
                TimeFormats.rfc3339(row.get("started_at")),
                TimeFormats.rfc3339(completedAt));
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

    private static String emptyIfNull(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return round2(numerator * 100d / denominator);
    }

    private static double round2(Double value) {
        if (value == null) {
            return 0d;
        }
        return Math.round(value * 100d) / 100d;
    }
}
