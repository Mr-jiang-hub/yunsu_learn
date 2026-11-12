package com.yunshu.zhixue.student.home;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.student.home.dto.HomeResponse;
import com.yunshu.zhixue.student.interview.InterviewQuotaService;
import com.yunshu.zhixue.student.interview.dto.InterviewQuotaView;
import com.yunshu.zhixue.student.learning.StudentLearningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * C 端首页聚合（04 §6.2 / §8.5）。
 *
 * <p>各区块各自取数、各自降级：任一区块抛异常只把该区块置 null（公告置空数组）并写入
 * {@code meta.degradedSections}，整体仍是 200，避免首页被单个慢/坏查询拖垮。
 * 整页按 studentId 做 60 秒 Caffeine 缓存；含降级结果的响应不写缓存，
 * 以便依赖恢复后立刻回到完整数据（04-Q50 方案 A）。</p>
 */
@Service
public class StudentHomeService {

    private static final Logger log = LoggerFactory.getLogger(StudentHomeService.class);

    private static final int STATUS_ENABLED = 1;
    private static final int PRACTICE_STAGE = 1;
    private static final int PRACTICE_IN_PROGRESS = 1;
    private static final int PRACTICE_FINISHED = 2;
    private static final int INTERVIEW_COMPLETED = 3;
    private static final int INTERVIEW_INCOMPLETE = 4;
    private static final int CACHE_TTL_SECONDS = 60;
    private static final int ACTIVITY_LIMIT = 5;
    private static final int NOTICE_LIMIT = 3;
    private static final int NOTICE_STATUS_PUBLISHED = 2;

    private final Cache<Long, HomeResponse> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(CACHE_TTL_SECONDS))
            .build();

    private final JdbcTemplate jdbcTemplate;
    private final InterviewQuotaService quotaService;
    private final StudentLearningService learningService;

    public StudentHomeService(JdbcTemplate jdbcTemplate,
                              InterviewQuotaService quotaService,
                              StudentLearningService learningService) {
        this.jdbcTemplate = jdbcTemplate;
        this.quotaService = quotaService;
        this.learningService = learningService;
    }

    public HomeResponse home(long studentId) {
        HomeResponse cached = cache.getIfPresent(studentId);
        if (cached != null) {
            return cached;
        }
        HomeResponse fresh = build(studentId);
        if (fresh.meta().degradedSections().isEmpty()) {
            cache.put(studentId, fresh);
        }
        return fresh;
    }

    // ------------------------------------------------------------------ 组装

    private HomeResponse build(long studentId) {
        Instant now = Instant.now();
        List<HomeResponse.DegradedSection> degraded = new ArrayList<>();

        Long pathId = currentPathId(studentId);
        StageSnapshot stage = null;
        if (pathId != null) {
            try {
                stage = loadStage(studentId, pathId);
            } catch (RuntimeException ex) {
                degraded.add(degrade("currentPath", ex));
            }
        }
        if (stage == null) {
            // 无主路线或路线已不可学：hasPath=false，其余学习区块为 null（04 §6.2.1 规则 1）
            return new HomeResponse(false, null, null, null, null,
                    activities(studentId, degraded), notices(degraded), meta(now, degraded));
        }

        HomeResponse.CurrentPathSection currentPath = null;
        try {
            currentPath = currentPathSection(studentId, stage);
        } catch (RuntimeException ex) {
            degraded.add(degrade("currentPath", ex));
        }

        HomeResponse.ContinueLearning continueLearning = null;
        try {
            continueLearning = continueLearning(studentId, stage);
        } catch (RuntimeException ex) {
            degraded.add(degrade("continueLearning", ex));
        }

        HomeResponse.PracticeSection practice = null;
        try {
            practice = practiceSection(studentId, stage);
        } catch (RuntimeException ex) {
            degraded.add(degrade("practice", ex));
        }

        HomeResponse.InterviewSection interview = null;
        try {
            interview = interviewSection(studentId);
        } catch (RuntimeException ex) {
            degraded.add(degrade("interview", ex));
        }

        return new HomeResponse(true, currentPath, continueLearning, practice, interview,
                activities(studentId, degraded), notices(degraded), meta(now, degraded));
    }

    private HomeResponse.CurrentPathSection currentPathSection(long studentId, StageSnapshot stage) {
        Double mastery = learningService.pathMasteryRate(studentId, stage.pathId());
        double masteryRate = mastery == null ? 0d : round2(mastery);
        return new HomeResponse.CurrentPathSection(
                stage.pathId(),
                stage.pathName(),
                stage.subjectName(),
                stage.stageCount(),
                stage.completedStageCount(),
                rate(stage.completedStageCount(), stage.stageCount()),
                masteryRate,
                mastery != null);
    }

    private HomeResponse.PracticeSection practiceSection(long studentId, StageSnapshot stage) {
        Long inProgressId = firstLong(
                "SELECT id FROM practice WHERE student_id = ? AND stage_id = ? AND type = ? AND status = ? "
                        + "AND expire_at > UTC_TIMESTAMP(3) ORDER BY id DESC LIMIT 1",
                studentId, stage.currentStageId(), PRACTICE_STAGE, PRACTICE_IN_PROGRESS);
        Object lastFinished = firstObject(
                "SELECT finished_at FROM practice WHERE student_id = ? AND stage_id = ? AND type = ? "
                        + "AND status = ? ORDER BY finished_at DESC, id DESC LIMIT 1",
                studentId, stage.currentStageId(), PRACTICE_STAGE, PRACTICE_FINISHED);
        return new HomeResponse.PracticeSection(
                stage.currentStageId(),
                stage.currentStageName(),
                stage.currentStageQuestionCount() > 0,
                inProgressId,
                TimeFormats.rfc3339(lastFinished));
    }

    private HomeResponse.InterviewSection interviewSection(long studentId) {
        InterviewQuotaView quota = quotaService.view(studentId);
        Long lastReportId = firstLong(
                "SELECT r.id FROM interview_report r JOIN interview_session s ON s.id = r.session_id "
                        + "WHERE s.student_id = ? AND r.status = 1 ORDER BY r.id DESC LIMIT 1",
                studentId);
        return new HomeResponse.InterviewSection(
                quota.quotaDate(), quota.limit(), quota.used(), quota.remaining(),
                quota.remaining() > 0, lastReportId);
    }

    /** 继续学习：当前阶段内"已开始未完成"优先，其次"未开始"；阶段无启用课程则 null */
    private HomeResponse.ContinueLearning continueLearning(long studentId, StageSnapshot stage) {
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(
                "SELECT c.id, c.title, cp.started_at, cp.completed_at FROM course c "
                        + "LEFT JOIN course_progress cp ON cp.course_id = c.id AND cp.student_id = ? "
                        + "WHERE c.stage_id = ? AND c.status = ? ORDER BY c.sort_no ASC, c.id ASC",
                studentId, stage.currentStageId(), STATUS_ENABLED);
        Map<String, Object> inProgress = null;
        Map<String, Object> notStarted = null;
        for (Map<String, Object> row : courses) {
            if (row.get("completed_at") != null) {
                continue;
            }
            if (row.get("started_at") != null) {
                inProgress = row;
                break;
            }
            if (notStarted == null) {
                notStarted = row;
            }
        }
        Map<String, Object> target = inProgress != null ? inProgress : notStarted;
        if (target == null) {
            return null;
        }
        return new HomeResponse.ContinueLearning(
                stage.currentStageId(),
                stage.currentStageName(),
                number(target.get("id")),
                String.valueOf(target.get("title")),
                inProgress != null ? "IN_PROGRESS" : "NOT_STARTED");
    }

    /** 最近动态：三类事件各自取最新若干条，合并后按时间倒序截断 */
    private List<HomeResponse.ActivityItem> activities(long studentId,
                                                       List<HomeResponse.DegradedSection> degraded) {
        List<HomeResponse.ActivityItem> items = new ArrayList<>();
        try {
            jdbcTemplate.query(
                    "SELECT c.title, cp.completed_at FROM course_progress cp "
                            + "JOIN course c ON c.id = cp.course_id "
                            + "WHERE cp.student_id = ? AND cp.completed_at IS NOT NULL "
                            + "ORDER BY cp.completed_at DESC, cp.id DESC LIMIT ?",
                    rs -> {
                        items.add(new HomeResponse.ActivityItem("COURSE_COMPLETED",
                                "完成课程《" + rs.getString("title") + "》",
                                TimeFormats.rfc3339(rs.getObject("completed_at"))));
                    },
                    studentId, ACTIVITY_LIMIT);
            jdbcTemplate.query(
                    "SELECT st.name, pr.finished_at FROM practice pr "
                            + "LEFT JOIN stage st ON st.id = pr.stage_id "
                            + "WHERE pr.student_id = ? AND pr.type = ? AND pr.status = ? "
                            + "ORDER BY pr.finished_at DESC, pr.id DESC LIMIT ?",
                    rs -> {
                        String stageName = rs.getString("name");
                        items.add(new HomeResponse.ActivityItem("PRACTICE_FINISHED",
                                stageName == null ? "完成阶段练习" : "完成「" + stageName + "」阶段练习",
                                TimeFormats.rfc3339(rs.getObject("finished_at"))));
                    },
                    studentId, PRACTICE_STAGE, PRACTICE_FINISHED, ACTIVITY_LIMIT);
            jdbcTemplate.query(
                    "SELECT stage_name, finished_at FROM interview_session "
                            + "WHERE student_id = ? AND status IN (?, ?) "
                            + "ORDER BY finished_at DESC, id DESC LIMIT ?",
                    rs -> {
                        items.add(new HomeResponse.ActivityItem("INTERVIEW_FINISHED",
                                "完成「" + rs.getString("stage_name") + "」AI 模拟面试",
                                TimeFormats.rfc3339(rs.getObject("finished_at"))));
                    },
                    studentId, INTERVIEW_COMPLETED, INTERVIEW_INCOMPLETE, ACTIVITY_LIMIT);
        } catch (RuntimeException ex) {
            degraded.add(degrade("recentActivities", ex));
        }
        items.removeIf(item -> item.occurredAt() == null);
        items.sort(Comparator.comparing(HomeResponse.ActivityItem::occurredAt).reversed());
        return items.size() > ACTIVITY_LIMIT ? List.copyOf(items.subList(0, ACTIVITY_LIMIT)) : List.copyOf(items);
    }

    /** 公告：已发布且未失效，置顶优先、再按排序号与发布时间；取前 3 条 */
    private List<HomeResponse.NoticeItem> notices(List<HomeResponse.DegradedSection> degraded) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, title, content, pinned, published_at FROM notice "
                            + "WHERE status = ? AND (expire_at IS NULL OR expire_at > UTC_TIMESTAMP(3)) "
                            + "ORDER BY pinned DESC, sort_no ASC, published_at DESC, id DESC LIMIT ?",
                    (rs, rowNum) -> new HomeResponse.NoticeItem(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("content"),
                            rs.getBoolean("pinned"),
                            TimeFormats.rfc3339(rs.getObject("published_at"))),
                    NOTICE_STATUS_PUBLISHED, NOTICE_LIMIT);
        } catch (RuntimeException ex) {
            degraded.add(degrade("notices", ex));
            return List.of();
        }
    }

    // ------------------------------------------------------------------ 路线与阶段
    /** 主路线 + 当前阶段（第一个未学完的启用阶段；全部学完则取最后一个启用阶段） */
    private StageSnapshot loadStage(long studentId, long pathId) {
        List<Map<String, Object>> paths = jdbcTemplate.queryForList(
                "SELECT p.id, p.name, s.name AS subject_name, "
                        + "(SELECT COUNT(*) FROM stage st WHERE st.path_id = p.id AND st.status = 1) AS stage_count, "
                        + "(SELECT COUNT(*) FROM stage st WHERE st.path_id = p.id AND st.status = 1 "
                        + "  AND EXISTS (SELECT 1 FROM course c WHERE c.stage_id = st.id AND c.status = 1) "
                        + "  AND NOT EXISTS (SELECT 1 FROM course c LEFT JOIN course_progress cp "
                        + "    ON cp.course_id = c.id AND cp.student_id = ? "
                        + "    WHERE c.stage_id = st.id AND c.status = 1 AND cp.completed_at IS NULL)) "
                        + "  AS completed_stage_count "
                        + "FROM learning_path p JOIN subject s ON s.id = p.subject_id "
                        + "WHERE p.id = ? AND p.status = 1 AND s.status = 1",
                studentId, pathId);
        if (paths.isEmpty()) {
            // 路线或学科被停用：不报错，按"无路线"处理（与 C-03 列表口径一致）
            return null;
        }
        Map<String, Object> path = paths.get(0);

        List<Map<String, Object>> stages = jdbcTemplate.queryForList(
                "SELECT st.id, st.name, "
                        + "(SELECT COUNT(*) FROM course c WHERE c.stage_id = st.id AND c.status = 1) AS course_count, "
                        + "(SELECT COUNT(*) FROM course_progress cp JOIN course c ON c.id = cp.course_id "
                        + " WHERE c.stage_id = st.id AND c.status = 1 AND cp.student_id = ? "
                        + " AND cp.completed_at IS NOT NULL) AS completed_count, "
                        + "(SELECT COUNT(*) FROM question q WHERE q.stage_id = st.id AND q.status = 1) AS question_count "
                        + "FROM stage st WHERE st.path_id = ? AND st.status = 1 "
                        + "ORDER BY st.sort_no ASC, st.id ASC",
                studentId, pathId);
        if (stages.isEmpty()) {
            return null;
        }
        Map<String, Object> current = stages.get(stages.size() - 1);
        for (Map<String, Object> row : stages) {
            if (intValue(row.get("completed_count")) < intValue(row.get("course_count"))) {
                current = row;
                break;
            }
        }

        return new StageSnapshot(
                pathId,
                String.valueOf(path.get("name")),
                String.valueOf(path.get("subject_name")),
                intValue(path.get("stage_count")),
                intValue(path.get("completed_stage_count")),
                number(current.get("id")),
                String.valueOf(current.get("name")),
                intValue(current.get("question_count")));
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

    // ------------------------------------------------------------------ 内部

    private HomeResponse.Meta meta(Instant now, List<HomeResponse.DegradedSection> degraded) {
        return new HomeResponse.Meta(TimeFormats.rfc3339(now), CACHE_TTL_SECONDS, List.copyOf(degraded));
    }

    private static HomeResponse.DegradedSection degrade(String section, RuntimeException ex) {
        if (ex instanceof BizException biz && biz.getErrorCode() == ErrorCode.UNAUTHENTICATED) {
            throw biz;
        }
        int code = ex instanceof BizException biz
                ? biz.getErrorCode().getCode() : ErrorCode.SYSTEM_ERROR.getCode();
        log.warn("首页区块降级: section={}, code={}, message={}", section, code, ex.getMessage());
        return new HomeResponse.DegradedSection(section, code, ex.getMessage());
    }

    private Long firstLong(String sql, Object... args) {
        Object value = firstObject(sql, args);
        return value == null ? null : ((Number) value).longValue();
    }

    private Object firstObject(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0).values().iterator().next();
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static int intValue(Object value) {
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return 0;
    }

    private static double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return round2(numerator * 100d / denominator);
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private record StageSnapshot(
            long pathId,
            String pathName,
            String subjectName,
            int stageCount,
            int completedStageCount,
            long currentStageId,
            String currentStageName,
            int currentStageQuestionCount
    ) {
    }
}
