package com.yunshu.zhixue.student.interview;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.idempotency.IdempotencyService;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.integration.ai.AsrProvider;
import com.yunshu.zhixue.integration.ai.AsrResponse;
import com.yunshu.zhixue.student.interview.dto.AudioTranscribeResponse;
import com.yunshu.zhixue.student.interview.dto.CreateSessionRequest;
import com.yunshu.zhixue.student.interview.dto.CreateSessionResponse;
import com.yunshu.zhixue.student.interview.dto.FinishResponse;
import com.yunshu.zhixue.student.interview.dto.InterviewMessageView;
import com.yunshu.zhixue.student.interview.dto.InterviewOptionsResponse;
import com.yunshu.zhixue.student.interview.dto.InterviewQuotaView;
import com.yunshu.zhixue.student.interview.dto.InterviewReportResponse;
import com.yunshu.zhixue.student.interview.dto.InterviewSessionDetailResponse;
import com.yunshu.zhixue.student.interview.dto.InterviewSessionListItem;
import com.yunshu.zhixue.student.interview.dto.InterviewSessionView;
import com.yunshu.zhixue.student.interview.dto.OpeningFailedData;
import com.yunshu.zhixue.student.interview.dto.SubmitAnswerRequest;
import com.yunshu.zhixue.student.interview.dto.SubmitAnswerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * C 端 AI 模拟面试（04 §6.7 / PRD 5.7）。
 *
 * <p>关键约束：</p>
 * <ol>
 *   <li>固定 4 轮（开场 1 + 追问 3），`interview_session.current_round` 语义是"已完成作答轮次"，
 *       对外 `currentRound` 是其 +1（当前待答轮次）。</li>
 *   <li>创建即扣次数，扣减与建会话同事务；只有"开场题未生成、会话未进入可作答状态"才作废并返还，
 *       正常完成/未完成/过期一律不返还（rules §10.4）。</li>
 *   <li>调大模型一律在事务外：先落回答，再出下一题；追问失败不回滚回答，按"未完成"结束并出报告。</li>
 *   <li>AI 失败绝不写 0 分：出题失败走 50002 作废，报告失败不建报告（报告接口 30051）。</li>
 *   <li>归属失败统一 403 + 30041，与"不存在"完全一致。</li>
 * </ol>
 */
@Service
public class StudentInterviewService {

    private static final Logger log = LoggerFactory.getLogger(StudentInterviewService.class);

    private static final int STATUS_CREATING = 1;
    private static final int STATUS_IN_PROGRESS = 2;
    private static final int STATUS_COMPLETED = 3;
    private static final int STATUS_INCOMPLETE = 4;
    private static final int STATUS_EXPIRED = 5;
    private static final int STATUS_VOIDED = 6;

    private static final int ROLE_AI = 1;
    private static final int ROLE_STUDENT = 2;

    private static final int REPORT_NORMAL = 1;
    private static final int REPORT_INCOMPLETE = 2;

    private static final int VOID_OPENING_FAILED = 1;
    private static final int VOID_CREATING_TIMEOUT = 2;

    private static final int MAX_ROUNDS = 4;
    private static final int STATUS_ENABLED = 1;
    private static final long REUSE_WINDOW_SECONDS = 60L;

    private static final long MAX_AUDIO_BYTES = 2L * 1024 * 1024;

    /** 语音白名单（04 §6.7.7）；浏览器 webm/opus 常上报 video/webm，故一并接受 */
    private static final Set<String> ALLOWED_AUDIO_TYPES = Set.of(
            "audio/webm", "video/webm", "audio/ogg", "application/ogg",
            "audio/mp4", "video/mp4", "audio/m4a", "audio/x-m4a",
            "audio/wav", "audio/x-wav", "audio/wave");

    private static final String SELECT_SESSION =
            "SELECT s.id, s.student_id, s.subject_id, sb.name AS subject_name, s.difficulty_id, "
                    + "df.code AS difficulty_code, df.name AS difficulty_name, s.stage_id, s.stage_name, "
                    + "s.status, s.current_round, s.quota_date, s.started_at, s.finished_at, s.expire_at, s.void_reason "
                    + "FROM interview_session s "
                    + "LEFT JOIN subject sb ON sb.id = s.subject_id "
                    + "LEFT JOIN interview_difficulty df ON df.id = s.difficulty_id ";

    /** 当前主路线下的启用学科（路线或学科停用即视为无路线） */
    private static final String SELECT_CURRENT_PATH =
            "SELECT p.id AS path_id, p.name AS path_name, p.subject_id, s.name AS subject_name "
                    + "FROM student st "
                    + "JOIN learning_path p ON p.id = st.current_path_id AND p.status = 1 "
                    + "JOIN subject s ON s.id = p.subject_id AND s.status = 1 "
                    + "WHERE st.id = ?";

    private static final String SELECT_FIRST_STAGE_WITH_QUESTIONS =
            "SELECT st.id, st.name FROM stage st "
                    + "JOIN question q ON q.stage_id = st.id AND q.status = 1 "
                    + "WHERE st.path_id = ? AND st.status = 1 "
                    + "GROUP BY st.id, st.name, st.sort_no "
                    + "ORDER BY st.sort_no ASC, st.id ASC LIMIT 1";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final InterviewQuotaService quotaService;
    private final InterviewAiClient aiClient;
    private final AsrProvider asrProvider;
    private final IdempotencyService idempotencyService;

    public StudentInterviewService(JdbcTemplate jdbcTemplate,
                                   PlatformTransactionManager txManager,
                                   InterviewQuotaService quotaService,
                                   InterviewAiClient aiClient,
                                   AsrProvider asrProvider,
                                   IdempotencyService idempotencyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(txManager);
        this.quotaService = quotaService;
        this.aiClient = aiClient;
        this.asrProvider = asrProvider;
        this.idempotencyService = idempotencyService;
    }

    // ------------------------------------------------------------------ 选项与配额

    /** 面试入口选项（04 §6.7.1）：只返回当前主路线下的启用学科，无路线返回空数组 */
    public InterviewOptionsResponse options(long studentId) {
        LocalDate quotaDate = quotaService.quotaDate();
        int remaining = quotaService.remaining(studentId);
        List<InterviewOptionsResponse.SubjectOption> items = new ArrayList<>(1);

        List<Map<String, Object>> paths = jdbcTemplate.queryForList(SELECT_CURRENT_PATH, studentId);
        if (!paths.isEmpty()) {
            Map<String, Object> path = paths.get(0);
            long subjectId = number(path.get("subject_id"));
            long pathId = number(path.get("path_id"));
            int dimensionCount = dimensionRows(subjectId).size();
            List<InterviewOptionsResponse.DifficultyOption> difficulties = difficultyOptions();
            List<Map<String, Object>> stageRows = jdbcTemplate.queryForList(
                    SELECT_FIRST_STAGE_WITH_QUESTIONS, pathId);
            boolean hasAnyStage = hasEnabledStage(pathId);

            Long stageId = null;
            String stageName = null;
            if (!stageRows.isEmpty()) {
                stageId = number(stageRows.get(0).get("id"));
                stageName = (String) stageRows.get(0).get("name");
            }

            String reasonCode = null;
            String reasonMessage = null;
            if (dimensionCount == 0) {
                reasonCode = "NO_DIMENSION";
                reasonMessage = "该学科尚未配置面试能力维度";
            } else if (difficulties.isEmpty()) {
                reasonCode = "NO_DIFFICULTY";
                reasonMessage = "面试难度档位已全部停用";
            } else if (stageId == null) {
                reasonCode = hasAnyStage ? "NO_QUESTION" : "NO_STAGE";
                reasonMessage = hasAnyStage ? "阶段内暂无启用题目" : "路线暂无启用阶段";
            } else if (remaining <= 0) {
                reasonCode = "QUOTA_EXHAUSTED";
                reasonMessage = "今日面试次数已用完";
            }

            items.add(new InterviewOptionsResponse.SubjectOption(
                    subjectId,
                    (String) path.get("subject_name"),
                    pathId,
                    (String) path.get("path_name"),
                    stageId,
                    stageName,
                    dimensionCount,
                    difficulties,
                    reasonCode == null,
                    reasonCode,
                    reasonMessage,
                    remaining));
        }

        return new InterviewOptionsResponse(items, quotaDate.toString(), serverTime());
    }

    /** 今日余量（04 §6.7.2） */
    public InterviewQuotaView quota(long studentId) {
        return quotaService.view(studentId);
    }

    // ------------------------------------------------------------------ 创建会话

    /** 创建会话并返回开场题（04 §6.7.3）；`Idempotency-Key` 必填 */
    public CreateSessionResponse create(long studentId, CreateSessionRequest request, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        String canonical = canonicalCreateRequest(studentId, request);

        // 幂等：同 key 同请求直接回首次结果（api-rules §9.2 / 05 G-04）
        Long replayed = replayedSessionId(idempotencyKey, canonical, studentId);
        if (replayed != null) {
            log.info("面试创建命中幂等记录: studentId={}, sessionId={}, key={}",
                    studentId, replayed, idempotencyKey);
            return buildCreateResponse(loadOwned(replayed, studentId), true);
        }
        idempotencyService.markFailure(IDEMPOTENCY_SCOPE, idempotencyKey, canonical, studentId);

        SessionRow reusable = findReusable(studentId, request.subjectId(), request.difficultyId());
        if (reusable != null) {
            log.info("面试创建复用同配置会话: studentId={}, sessionId={}", studentId, reusable.id());
            idempotencyService.save(IDEMPOTENCY_SCOPE, idempotencyKey, canonical, studentId,
                    sessionIdJson(reusable.id()));
            return buildCreateResponse(reusable, true);
        }

        Config config = resolveConfig(studentId, request.subjectId(), request.difficultyId());
        LocalDate quotaDate = quotaService.quotaDate();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        long sessionId = txTemplate.execute(status -> {
            if (!quotaService.tryOccupy(studentId, quotaDate)) {
                log.info("面试次数已用完: studentId={}, quotaDate={}", studentId, quotaDate);
                throw new BizException(ErrorCode.INTERVIEW_QUOTA_EXHAUSTED,
                        ErrorCode.INTERVIEW_QUOTA_EXHAUSTED.getMessage(), quotaService.view(studentId));
            }
            long id = insertSession(studentId, config, quotaDate, now);
            log.info("面试会话创建（CREATING）: studentId={}, sessionId={}, quotaDate={}, key={}",
                    studentId, id, quotaDate, idempotencyKey);
            return id;
        });

        // 调大模型必须在事务外（rules 禁止事务内调大模型）
        String question = aiClient.openingQuestion(config.context());
        if (question == null) {
            voidSession(sessionId, VOID_OPENING_FAILED);
            InterviewQuotaView quota = quotaService.view(studentId);
            log.warn("面试开场题生成失败，已作废并返还次数: studentId={}, sessionId={}", studentId, sessionId);
            throw new BizException(ErrorCode.INTERVIEW_OPENING_FAILED,
                    ErrorCode.INTERVIEW_OPENING_FAILED.getMessage(),
                    new OpeningFailedData(sessionId, true, quota));
        }

        LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "UPDATE interview_session SET status = ?, started_at = ?, expire_at = ? "
                            + "WHERE id = ? AND status = ?",
                    STATUS_IN_PROGRESS, startedAt, startedAt.plusHours(24), sessionId, STATUS_CREATING);
            insertMessage(sessionId, 1, ROLE_AI, question, startedAt);
        });

        // 成功才写幂等结果：失败留给 markFailure，客户端可用同一个 key 重试
        idempotencyService.save(IDEMPOTENCY_SCOPE, idempotencyKey, canonical, studentId,
                sessionIdJson(sessionId));
        return buildCreateResponse(loadSession(sessionId), false);
    }

    /** 幂等作用域；与 subject_id 一起保证不同学员的同 key 互不影响 */
    private static final String IDEMPOTENCY_SCOPE = "interview.create";

    /** 规范化请求：把"同一次创建"钉死成固定字符串，顺序固定，避免同义请求算出不同 hash */
    private static String canonicalCreateRequest(long studentId, CreateSessionRequest request) {
        return studentId + "|" + request.subjectId() + "|" + request.difficultyId();
    }

    private static String sessionIdJson(long sessionId) {
        return "{\"sessionId\":" + sessionId + "}";
    }

    /** 解析幂等记录里的 sessionId；记录不存在/无响应/会话已不存在时返回 null 走正常创建 */
    private Long replayedSessionId(String key, String canonical, long studentId) {
        String json = idempotencyService.findResponse(IDEMPOTENCY_SCOPE, key, canonical, studentId);
        if (json == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"sessionId\":\\s*(\\d+)")
                .matcher(json);
        if (!matcher.find()) {
            return null;
        }
        long sessionId = Long.parseLong(matcher.group(1));
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_session WHERE id = ? AND student_id = ? AND status <> ?",
                Integer.class, sessionId, studentId, STATUS_VOIDED);
        return exists != null && exists > 0 ? sessionId : null;
    }

    // ------------------------------------------------------------------ 列表与详情

    /** 面试历史（04 §6.7.4）；`VOIDED` 也返回，前端单独标注"已作废/次数已返还" */
    public PageData<InterviewSessionListItem> list(long studentId, PageQuery pageQuery, Long subjectId,
                                                  String status, LocalDate startDate, LocalDate endDate) {
        StringBuilder from = new StringBuilder(
                "FROM interview_session s "
                        + "LEFT JOIN subject sb ON sb.id = s.subject_id "
                        + "LEFT JOIN interview_difficulty df ON df.id = s.difficulty_id "
                        + "LEFT JOIN interview_report r ON r.session_id = s.id "
                        + "WHERE s.student_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(studentId);

        if (subjectId != null) {
            from.append(" AND s.subject_id = ?");
            params.add(subjectId);
        }
        if (status != null && !status.isBlank()) {
            from.append(" AND s.status = ?");
            params.add(statusCode(status));
        }
        if (startDate != null) {
            from.append(" AND s.quota_date >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            from.append(" AND s.quota_date <= ?");
            params.add(endDate);
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + from, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageQuery.getSize());
        pageParams.add(pageQuery.offset());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.id, s.subject_id, sb.name AS subject_name, df.code AS difficulty_code, "
                        + "df.name AS difficulty_name, s.stage_name, s.status, s.current_round, "
                        + "s.started_at, s.finished_at, s.quota_date, r.total_score, r.status AS report_status "
                        + from + " ORDER BY s.started_at DESC, s.id DESC LIMIT ? OFFSET ?",
                pageParams.toArray());

        List<InterviewSessionListItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            int rowStatus = intValue(row.get("status"));
            int currentRound = intValue(row.get("current_round"));
            String reportStatus = row.get("report_status") == null
                    ? null : reportStatusText(intValue(row.get("report_status")));
            items.add(new InterviewSessionListItem(
                    number(row.get("id")),
                    number(row.get("subject_id")),
                    (String) row.get("subject_name"),
                    (String) row.get("difficulty_code"),
                    (String) row.get("difficulty_name"),
                    (String) row.get("stage_name"),
                    statusText(rowStatus),
                    rowStatus == STATUS_COMPLETED ? MAX_ROUNDS : Math.min(MAX_ROUNDS, currentRound + 1),
                    TimeFormats.rfc3339(row.get("started_at")),
                    TimeFormats.rfc3339(row.get("finished_at")),
                    row.get("total_score") == null ? null : ((Number) row.get("total_score")).doubleValue(),
                    reportStatus,
                    row.get("quota_date") == null ? null : row.get("quota_date").toString()));
        }
        return PageData.of(items, pageQuery.getPage(), pageQuery.getSize(), total == null ? 0L : total);
    }

    /** 会话详情与续答（04 §6.7.5）：断线重进不生成新问题、不重复扣次 */
    public InterviewSessionDetailResponse detail(long sessionId, long studentId) {
        SessionRow session = expireIfNeeded(loadOwned(sessionId, studentId));
        List<InterviewMessageView> messages = messages(sessionId);
        InterviewMessageView currentQuestion = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("AI".equals(messages.get(i).role())) {
                currentQuestion = messages.get(i);
                break;
            }
        }
        boolean active = session.status() == STATUS_IN_PROGRESS;
        return new InterviewSessionDetailResponse(
                view(session), messages, currentQuestion, active, active, hasReport(sessionId));
    }

    // ------------------------------------------------------------------ 作答

    /** 提交一轮作答（04 §6.7.6） */
    public SubmitAnswerResponse submitAnswer(long sessionId, long studentId, SubmitAnswerRequest request) {
        SessionRow session = expireIfNeeded(loadOwned(sessionId, studentId));
        assertAnswerable(session);

        int round = request.round();
        if (round < 1 || round > MAX_ROUNDS) {
            throw new BizException(ErrorCode.INTERVIEW_ROUND_INVALID);
        }
        String answerText = request.answerText() == null ? "" : request.answerText().trim();
        if (answerText.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回答内容不能为空");
        }

        // 先处理同轮重复提交：网络重试不能因为 current_round 已推进而被误判为"轮次非法"
        InterviewMessageView existing = messageOf(sessionId, round, ROLE_STUDENT);
        if (existing != null) {
            return replay(session, existing, answerText);
        }
        if (round != session.currentRound() + 1) {
            throw new BizException(ErrorCode.INTERVIEW_ROUND_INVALID);
        }

        try {
            txTemplate.executeWithoutResult(status -> {
                insertMessage(sessionId, round, ROLE_STUDENT, answerText, LocalDateTime.now(ZoneOffset.UTC));
                int updated = jdbcTemplate.update(
                        "UPDATE interview_session SET current_round = ? "
                                + "WHERE id = ? AND status = ? AND current_round = ?",
                        round, sessionId, STATUS_IN_PROGRESS, round - 1);
                if (updated == 0) {
                    throw new BizException(ErrorCode.INTERVIEW_ROUND_SUBMITTED);
                }
            });
        } catch (DuplicateKeyException ex) {
            // 并发同轮提交：唯一键 uk_interview_message_round_role 兜底，回读后按重放处理
            log.info("面试同一轮并发提交: sessionId={}, round={}", sessionId, round);
            InterviewMessageView raced = messageOf(sessionId, round, ROLE_STUDENT);
            if (raced == null) {
                throw new BizException(ErrorCode.INTERVIEW_ROUND_SUBMITTED);
            }
            return replay(expireIfNeeded(loadSession(sessionId)), raced, answerText);
        }

        InterviewMessageView studentMessage = messageOf(sessionId, round, ROLE_STUDENT);
        if (round == MAX_ROUNDS) {
            return completeSession(session, studentMessage);
        }
        return continueSession(session, round, studentMessage);
    }

    /** 语音转写（04 §6.7.7）：只转写、不落库、不推进轮次，音频不写磁盘 */
    public AudioTranscribeResponse transcribe(long sessionId, long studentId, Integer round, MultipartFile audio) {
        SessionRow session = expireIfNeeded(loadOwned(sessionId, studentId));
        assertAnswerable(session);

        int expected = session.currentRound() + 1;
        if (round == null || round < 1 || round > MAX_ROUNDS || round != expected) {
            throw new BizException(ErrorCode.INTERVIEW_ROUND_INVALID);
        }
        if (audio == null || audio.isEmpty()) {
            throw new BizException(ErrorCode.AUDIO_EMPTY);
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new BizException(ErrorCode.AUDIO_TOO_LARGE);
        }
        String contentType = normalizeContentType(audio.getContentType(), audio.getOriginalFilename());
        if (!ALLOWED_AUDIO_TYPES.contains(contentType)) {
            throw new BizException(ErrorCode.AUDIO_FORMAT_UNSUPPORTED);
        }

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException ex) {
            log.warn("读取上传音频失败: sessionId={}", sessionId, ex);
            throw new BizException(ErrorCode.AUDIO_EMPTY);
        }
        if (bytes.length == 0) {
            throw new BizException(ErrorCode.AUDIO_EMPTY);
        }

        try {
            AsrResponse response = asrProvider.transcribe(bytes, contentType, null);
            if (response == null || response.degraded()
                    || response.transcript() == null || response.transcript().isBlank()) {
                log.info("ASR 降级为文字作答: sessionId={}, round={}", sessionId, round);
                return new AudioTranscribeResponse(round, "", true, true, null);
            }
            return new AudioTranscribeResponse(round, response.transcript().trim(), false, false, null);
        } catch (RuntimeException ex) {
            log.warn("ASR 调用异常，降级为文字作答: sessionId={}, round={}", sessionId, round, ex);
            return new AudioTranscribeResponse(round, "", true, true, null);
        }
    }

    /** 提前结束（04 §6.7.8）：仅在 IN_PROGRESS 可结束，重复调用返回首次结果 */
    public FinishResponse finish(long sessionId, long studentId, boolean confirm) {
        SessionRow session = expireIfNeeded(loadOwned(sessionId, studentId));
        if (session.status() == STATUS_COMPLETED || session.status() == STATUS_INCOMPLETE) {
            return new FinishResponse(sessionId, statusText(session.status()),
                    TimeFormats.rfc3339(session.finishedAt()), session.currentRound(), hasReport(sessionId));
        }
        if (session.status() != STATUS_IN_PROGRESS) {
            throw new BizException(ErrorCode.INTERVIEW_FINISH_NOT_ALLOWED);
        }
        if (!confirm) {
            throw new BizException(ErrorCode.INTERVIEW_ROUND_UNFINISHED);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int updated = jdbcTemplate.update(
                "UPDATE interview_session SET status = ?, finished_at = ? WHERE id = ? AND status = ?",
                STATUS_INCOMPLETE, now, sessionId, STATUS_IN_PROGRESS);
        boolean ready = updated == 1 && generateReport(sessionId, true);
        log.info("面试提前结束: sessionId={}, answeredRounds={}, updated={}, reportReady={}",
                sessionId, session.currentRound(), updated, ready);
        return new FinishResponse(sessionId, statusText(STATUS_INCOMPLETE), TimeFormats.rfc3339(now),
                session.currentRound(), ready);
    }

    /** 面试报告（04 §6.7.9） */
    public InterviewReportResponse report(long sessionId, long studentId) {
        SessionRow session = expireIfNeeded(loadOwned(sessionId, studentId));
        if (session.status() == STATUS_VOIDED || session.status() == STATUS_EXPIRED) {
            throw new BizException(ErrorCode.INTERVIEW_REPORT_ABSENT);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, total_score, summary, suggestions, status, created_at "
                        + "FROM interview_report WHERE session_id = ?", sessionId);
        if (rows.isEmpty()) {
            if (session.status() == STATUS_COMPLETED || session.status() == STATUS_INCOMPLETE) {
                // 没有有效作答的会话不会生成报告，按"无报告"而不是"生成中"返回
                throw new BizException(hasStudentAnswer(sessionId)
                        ? ErrorCode.INTERVIEW_REPORT_PENDING
                        : ErrorCode.INTERVIEW_REPORT_ABSENT);
            }
            throw new BizException(ErrorCode.INTERVIEW_REPORT_ABSENT);
        }
        Map<String, Object> row = rows.get(0);
        long reportId = number(row.get("id"));

        List<InterviewReportResponse.DimensionScore> dimensions = new ArrayList<>();
        List<Map<String, Object>> scoreRows = jdbcTemplate.queryForList(
                "SELECT dimension_id, dimension_name, score FROM report_dimension "
                        + "WHERE report_id = ? ORDER BY id ASC", reportId);
        for (Map<String, Object> dimensionRow : scoreRows) {
            dimensions.add(new InterviewReportResponse.DimensionScore(
                    number(dimensionRow.get("dimension_id")),
                    (String) dimensionRow.get("dimension_name"),
                    ((Number) dimensionRow.get("score")).doubleValue()));
        }

        int reportStatus = intValue(row.get("status"));
        return new InterviewReportResponse(
                sessionId,
                reportStatusText(reportStatus),
                ((Number) row.get("total_score")).doubleValue(),
                (String) row.get("summary"),
                splitSuggestions((String) row.get("suggestions")),
                dimensions,
                TimeFormats.rfc3339(row.get("created_at")));
    }

    // ------------------------------------------------------------------ 会话推进

    private SubmitAnswerResponse continueSession(SessionRow session, int round, InterviewMessageView studentMessage) {
        List<InterviewAiClient.Turn> turns = turns(session.id());
        String question = aiClient.followUp(contextOf(session), turns, round + 1);
        if (question == null) {
            // 追问失败：不回滚已保存回答，按"未完成"结束并出未完成报告（rules 11.1.3）
            log.warn("面试追问生成失败，按未完成结束: sessionId={}, round={}", session.id(), round);
            markFinished(session.id(), STATUS_INCOMPLETE);
            boolean ready = generateReport(session.id(), true);
            return new SubmitAnswerResponse(studentMessage, null, true, statusText(STATUS_INCOMPLETE),
                    true, ready, new SubmitAnswerResponse.Progress(round, MAX_ROUNDS), false);
        }
        txTemplate.executeWithoutResult(status ->
                insertMessage(session.id(), round + 1, ROLE_AI, question, LocalDateTime.now(ZoneOffset.UTC)));
        return new SubmitAnswerResponse(studentMessage, messageOf(session.id(), round + 1, ROLE_AI),
                false, statusText(STATUS_IN_PROGRESS), false, false,
                new SubmitAnswerResponse.Progress(round, round + 1), false);
    }

    private SubmitAnswerResponse completeSession(SessionRow session, InterviewMessageView studentMessage) {
        markFinished(session.id(), STATUS_COMPLETED);
        boolean ready = generateReport(session.id(), false);
        log.info("面试完成: sessionId={}, reportReady={}", session.id(), ready);
        return new SubmitAnswerResponse(studentMessage, null, true, statusText(STATUS_COMPLETED),
                false, ready, new SubmitAnswerResponse.Progress(MAX_ROUNDS, MAX_ROUNDS), false);
    }

    /** 同一轮重复提交：内容一致返回首次结果，不一致返回 30047（不覆盖、不重新生成） */
    private SubmitAnswerResponse replay(SessionRow session, InterviewMessageView existing, String answerText) {
        if (!existing.content().equals(answerText)) {
            throw new BizException(ErrorCode.INTERVIEW_ROUND_SUBMITTED);
        }
        boolean finished = session.status() == STATUS_COMPLETED || session.status() == STATUS_INCOMPLETE;
        InterviewMessageView next = finished ? null : messageOf(session.id(), existing.round() + 1, ROLE_AI);
        int answered = session.currentRound();
        return new SubmitAnswerResponse(existing, next, finished, statusText(session.status()), false,
                hasReport(session.id()),
                new SubmitAnswerResponse.Progress(answered, finished ? MAX_ROUNDS : answered + 1), true);
    }

    private void markFinished(long sessionId, int targetStatus) {
        jdbcTemplate.update(
                "UPDATE interview_session SET status = ?, finished_at = ? WHERE id = ? AND status = ?",
                targetStatus, LocalDateTime.now(ZoneOffset.UTC), sessionId, STATUS_IN_PROGRESS);
    }

    /** 报告生成：失败不建报告（报告接口返回 30051），绝不用 0 分表示失败 */
    private boolean generateReport(long sessionId, boolean incomplete) {
        if (hasReport(sessionId)) {
            return true;
        }
        // 一轮都没答就结束的会话视为"无有效作答"，不生成报告（04 §6.7.9 → 30052）
        if (!hasStudentAnswer(sessionId)) {
            log.info("面试无有效作答，不生成报告: sessionId={}", sessionId);
            return false;
        }
        List<InterviewAiClient.Turn> turns = turns(sessionId);
        if (turns.isEmpty()) {
            return false;
        }
        SessionRow session = loadSession(sessionId);
        InterviewAiClient.ReportDraft draft = aiClient.report(contextOf(session), turns, incomplete);
        if (draft == null) {
            log.warn("面试报告生成失败，暂不建报告: sessionId={}", sessionId);
            return false;
        }
        List<Map<String, Object>> dimensions = dimensionRows(session.subjectId());
        String suggestions = String.join("\n", draft.suggestions());

        txTemplate.executeWithoutResult(status -> {
            long reportId = insertReport(sessionId, draft, suggestions, incomplete);
            for (Map<String, Object> dimension : dimensions) {
                String name = (String) dimension.get("name");
                double score = draft.dimensionScores().getOrDefault(name, draft.totalScore());
                jdbcTemplate.update(
                        "INSERT INTO report_dimension (report_id, dimension_id, dimension_name, score) "
                                + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE score = VALUES(score)",
                        reportId, number(dimension.get("id")), name, round2(score));
            }
        });
        log.info("面试报告已生成: sessionId={}, totalScore={}, dimensions={}, incomplete={}",
                sessionId, draft.totalScore(), dimensions.size(), incomplete);
        return true;
    }

    // ------------------------------------------------------------------ 状态与过期

    /** 进行中过期即置 EXPIRED；创建中超 5 分钟即置 VOIDED 并返还次数（幂等） */
    private SessionRow expireIfNeeded(SessionRow session) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (session.status() == STATUS_IN_PROGRESS && session.expireAt() != null
                && session.expireAt().isBefore(now)) {
            int updated = jdbcTemplate.update(
                    "UPDATE interview_session SET status = ?, finished_at = ? WHERE id = ? AND status = ?",
                    STATUS_EXPIRED, now, session.id(), STATUS_IN_PROGRESS);
            if (updated == 1) {
                log.info("面试已过期: sessionId={}", session.id());
            }
            return loadSession(session.id(), session.studentId());
        }
        if (session.status() == STATUS_CREATING && session.expireAt() != null
                && session.expireAt().isBefore(now)) {
            voidSession(session.id(), VOID_CREATING_TIMEOUT);
            return loadSession(session.id(), session.studentId());
        }
        return session;
    }

    /** 作废并返还次数；条件更新保证只返还一次 */
    private void voidSession(long sessionId, int reason) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int updated = jdbcTemplate.update(
                "UPDATE interview_session SET status = ?, void_reason = ?, finished_at = ? "
                        + "WHERE id = ? AND status IN (?, ?)",
                STATUS_VOIDED, reason, now, sessionId, STATUS_CREATING, STATUS_IN_PROGRESS);
        if (updated == 1) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT student_id, quota_date FROM interview_session WHERE id = ?", sessionId);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                quotaService.refund(number(row.get("student_id")), toLocalDate(row.get("quota_date")));
                log.info("面试作废并返还次数: sessionId={}, reason={}", sessionId, reason);
            }
        }
    }

    private void assertAnswerable(SessionRow session) {
        switch (session.status()) {
            case STATUS_EXPIRED -> throw new BizException(ErrorCode.INTERVIEW_EXPIRED);
            case STATUS_VOIDED -> throw new BizException(ErrorCode.INTERVIEW_VOIDED);
            case STATUS_COMPLETED -> throw new BizException(ErrorCode.INTERVIEW_COMPLETED);
            case STATUS_IN_PROGRESS -> {
                // 可作答
            }
            default -> throw new BizException(ErrorCode.INTERVIEW_NOT_IN_PROGRESS);
        }
    }

    // ------------------------------------------------------------------ 配置校验

    /** 创建前校验配置：学科、路线、阶段、维度、难度，任一缺失都不扣次数 */
    private Config resolveConfig(long studentId, long subjectId, long difficultyId) {
        List<Map<String, Object>> paths = jdbcTemplate.queryForList(SELECT_CURRENT_PATH, studentId);
        if (paths.isEmpty()) {
            throw new BizException(ErrorCode.INTERVIEW_CONFIG_INCOMPLETE, "当前无主路线或路线已停用");
        }
        Map<String, Object> path = paths.get(0);
        if (number(path.get("subject_id")) != subjectId) {
            throw new BizException(ErrorCode.INTERVIEW_CONFIG_INCOMPLETE, "所选学科不在当前学习路线内");
        }
        long pathId = number(path.get("path_id"));
        List<String> dimensions = dimensionNames(subjectId);
        if (dimensions.isEmpty()) {
            throw new BizException(ErrorCode.INTERVIEW_CONFIG_INCOMPLETE, "该学科尚未配置面试能力维度");
        }

        List<Map<String, Object>> difficulties = jdbcTemplate.queryForList(
                "SELECT id, code, name, focus, status FROM interview_difficulty WHERE id = ?", difficultyId);
        if (difficulties.isEmpty()) {
            throw new BizException(ErrorCode.INTERVIEW_DIFFICULTY_DISABLED, "难度档位不存在");
        }
        Map<String, Object> difficulty = difficulties.get(0);
        if (intValue(difficulty.get("status")) != STATUS_ENABLED) {
            throw new BizException(ErrorCode.INTERVIEW_DIFFICULTY_DISABLED);
        }

        List<Map<String, Object>> stages = jdbcTemplate.queryForList(SELECT_FIRST_STAGE_WITH_QUESTIONS, pathId);
        if (stages.isEmpty()) {
            throw new BizException(ErrorCode.INTERVIEW_NO_STAGE);
        }
        Map<String, Object> stage = stages.get(0);
        long stageId = number(stage.get("id"));
        String stageName = (String) stage.get("name");

        String focus = (String) difficulty.get("focus");
        InterviewAiClient.Context context = new InterviewAiClient.Context(
                (String) path.get("subject_name"), stageName, (String) difficulty.get("name"),
                focus, dimensions, sampleQuestions(stageId));

        return new Config(subjectId, number(difficulty.get("id")), (String) difficulty.get("code"),
                (String) difficulty.get("name"), stageId, stageName, context);
    }

    private InterviewAiClient.Context contextOf(SessionRow session) {
        List<String> dimensions = dimensionNames(session.subjectId());
        List<Map<String, Object>> difficulties = jdbcTemplate.queryForList(
                "SELECT focus FROM interview_difficulty WHERE id = ?", session.difficultyId());
        String focus = difficulties.isEmpty() ? null : (String) difficulties.get(0).get("focus");
        return new InterviewAiClient.Context(session.subjectName(), session.stageName(),
                session.difficultyName(), focus, dimensions, sampleQuestions(session.stageId()));
    }

    private SessionRow findReusable(long studentId, long subjectId, long difficultyId) {
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(REUSE_WINDOW_SECONDS);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_SESSION + "WHERE s.student_id = ? AND s.subject_id = ? AND s.difficulty_id = ? "
                        + "AND s.status = ? AND s.created_at >= ? ORDER BY s.id DESC LIMIT 1",
                studentId, subjectId, difficultyId, STATUS_IN_PROGRESS, since);
        return rows.isEmpty() ? null : toSessionRow(rows.get(0));
    }

    // ------------------------------------------------------------------ 查询辅助

    private SessionRow loadOwned(long sessionId, long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_SESSION + "WHERE s.id = ? AND s.student_id = ?", sessionId, studentId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        return toSessionRow(rows.get(0));
    }

    private SessionRow loadSession(long sessionId) {
        return loadSession(sessionId, null);
    }

    private SessionRow loadSession(long sessionId, Long studentId) {
        List<Map<String, Object>> rows = studentId == null
                ? jdbcTemplate.queryForList(SELECT_SESSION + "WHERE s.id = ?", sessionId)
                : jdbcTemplate.queryForList(SELECT_SESSION + "WHERE s.id = ? AND s.student_id = ?",
                sessionId, studentId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        return toSessionRow(rows.get(0));
    }

    private SessionRow toSessionRow(Map<String, Object> row) {
        return new SessionRow(
                number(row.get("id")),
                number(row.get("student_id")),
                number(row.get("subject_id")),
                (String) row.get("subject_name"),
                number(row.get("difficulty_id")),
                (String) row.get("difficulty_code"),
                (String) row.get("difficulty_name"),
                number(row.get("stage_id")),
                (String) row.get("stage_name"),
                intValue(row.get("status")),
                intValue(row.get("current_round")),
                toLocalDate(row.get("quota_date")),
                toLocalDateTime(row.get("started_at")),
                toLocalDateTime(row.get("finished_at")),
                toLocalDateTime(row.get("expire_at")),
                row.get("void_reason") == null ? null : intValue(row.get("void_reason")));
    }

    private List<InterviewMessageView> messages(long sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, round_no, role, content, created_at FROM interview_message "
                        + "WHERE session_id = ? ORDER BY round_no ASC, role ASC", sessionId);
        List<InterviewMessageView> views = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            views.add(toMessageView(row));
        }
        return views;
    }

    private InterviewMessageView messageOf(long sessionId, int round, int role) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, round_no, role, content, created_at FROM interview_message "
                        + "WHERE session_id = ? AND round_no = ? AND role = ?", sessionId, round, role);
        return rows.isEmpty() ? null : toMessageView(rows.get(0));
    }

    private InterviewMessageView toMessageView(Map<String, Object> row) {
        int role = intValue(row.get("role"));
        return new InterviewMessageView(
                number(row.get("id")),
                intValue(row.get("round_no")),
                role == ROLE_AI ? "AI" : "STUDENT",
                (String) row.get("content"),
                TimeFormats.rfc3339(row.get("created_at")));
    }

    /** 组装轮次对话：每轮 AI 问题 + 学员回答 */
    private List<InterviewAiClient.Turn> turns(long sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT round_no, role, content FROM interview_message "
                        + "WHERE session_id = ? ORDER BY round_no ASC, role ASC", sessionId);
        Map<Integer, String> questions = new LinkedHashMap<>();
        Map<Integer, String> answers = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int round = intValue(row.get("round_no"));
            String content = (String) row.get("content");
            if (intValue(row.get("role")) == ROLE_AI) {
                questions.put(round, content);
            } else {
                answers.put(round, content);
            }
        }
        List<InterviewAiClient.Turn> turns = new ArrayList<>(questions.size());
        questions.forEach((round, question) ->
                turns.add(new InterviewAiClient.Turn(round, question, answers.getOrDefault(round, "（未作答）"))));
        return turns;
    }

    private boolean hasReport(long sessionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_report WHERE session_id = ?", Long.class, sessionId);
        return count != null && count > 0;
    }

    private boolean hasStudentAnswer(long sessionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_message WHERE session_id = ? AND role = ?",
                Long.class, sessionId, ROLE_STUDENT);
        return count != null && count > 0;
    }

    private List<Map<String, Object>> dimensionRows(long subjectId) {
        return jdbcTemplate.queryForList(
                "SELECT id, name FROM dimension WHERE subject_id = ? AND status = 1 "
                        + "ORDER BY sort_no ASC, id ASC", subjectId);
    }

    private List<String> dimensionNames(long subjectId) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : dimensionRows(subjectId)) {
            names.add((String) row.get("name"));
        }
        return names;
    }

    private List<String> sampleQuestions(long stageId) {
        List<String> contents = jdbcTemplate.queryForList(
                "SELECT content FROM question WHERE stage_id = ? AND status = 1 ORDER BY id ASC LIMIT 3",
                String.class, stageId);
        List<String> samples = new ArrayList<>(contents.size());
        for (String content : contents) {
            String text = content == null ? "" : content.replaceAll("\\s+", " ").trim();
            samples.add(text.length() <= 80 ? text : text.substring(0, 80) + "…");
        }
        return samples;
    }

    private List<InterviewOptionsResponse.DifficultyOption> difficultyOptions() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, code, name, description, focus, status FROM interview_difficulty "
                        + "WHERE status = 1 ORDER BY sort_no ASC, id ASC");
        List<InterviewOptionsResponse.DifficultyOption> options = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            options.add(new InterviewOptionsResponse.DifficultyOption(
                    number(row.get("id")),
                    (String) row.get("code"),
                    (String) row.get("name"),
                    (String) row.get("description"),
                    (String) row.get("focus"),
                    "ENABLED"));
        }
        return options;
    }

    private boolean hasEnabledStage(long pathId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stage WHERE path_id = ? AND status = 1", Long.class, pathId);
        return count != null && count > 0;
    }

    // ------------------------------------------------------------------ 写入辅助

    private long insertSession(long studentId, Config config, LocalDate quotaDate, LocalDateTime now) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO interview_session (student_id, subject_id, difficulty_id, stage_id, stage_name, "
                            + "status, current_round, quota_date, expire_at, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, studentId);
            ps.setLong(2, config.subjectId());
            ps.setLong(3, config.difficultyId());
            ps.setLong(4, config.stageId());
            ps.setString(5, config.stageName());
            ps.setInt(6, STATUS_CREATING);
            ps.setObject(7, quotaDate);
            ps.setObject(8, now.plusMinutes(5));
            ps.setObject(9, now);
            ps.setObject(10, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        return key.longValue();
    }

    private void insertMessage(long sessionId, int round, int role, String content, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO interview_message (session_id, round_no, role, content, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                sessionId, round, role, content, createdAt, createdAt);
    }

    private long insertReport(long sessionId, InterviewAiClient.ReportDraft draft, String suggestions,
                              boolean incomplete) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO interview_report (session_id, total_score, summary, suggestions, status) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, sessionId);
            ps.setDouble(2, round2(draft.totalScore()));
            ps.setString(3, draft.summary());
            ps.setString(4, suggestions);
            ps.setInt(5, incomplete ? REPORT_INCOMPLETE : REPORT_NORMAL);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        return key.longValue();
    }

    // ------------------------------------------------------------------ 视图组装

    private CreateSessionResponse buildCreateResponse(SessionRow session, boolean reused) {
        return new CreateSessionResponse(
                view(session),
                messageOf(session.id(), 1, ROLE_AI),
                quotaService.view(session.studentId()),
                reused);
    }

    private InterviewSessionView view(SessionRow session) {
        int currentRound = session.status() == STATUS_COMPLETED
                ? MAX_ROUNDS
                : Math.min(MAX_ROUNDS, session.currentRound() + 1);
        return new InterviewSessionView(
                session.id(),
                session.subjectId(),
                session.subjectName(),
                session.difficultyId(),
                session.difficultyCode(),
                session.difficultyName(),
                session.stageId(),
                session.stageName(),
                statusText(session.status()),
                currentRound,
                session.quotaDate() == null ? null : session.quotaDate().toString(),
                TimeFormats.rfc3339(session.startedAt()),
                TimeFormats.rfc3339(session.expireAt()),
                TimeFormats.rfc3339(session.finishedAt()),
                session.voidReason() == null ? null : voidReasonText(session.voidReason()));
    }

    // ------------------------------------------------------------------ 枚举与解析

    private static String statusText(int status) {
        return switch (status) {
            case STATUS_CREATING -> "CREATING";
            case STATUS_IN_PROGRESS -> "IN_PROGRESS";
            case STATUS_COMPLETED -> "COMPLETED";
            case STATUS_INCOMPLETE -> "INCOMPLETE";
            case STATUS_EXPIRED -> "EXPIRED";
            case STATUS_VOIDED -> "VOIDED";
            default -> "UNKNOWN";
        };
    }

    private static String reportStatusText(int status) {
        return status == REPORT_INCOMPLETE ? "INCOMPLETE" : "NORMAL";
    }

    private static String voidReasonText(int reason) {
        return switch (reason) {
            case VOID_OPENING_FAILED -> "OPENING_FAILED";
            case VOID_CREATING_TIMEOUT -> "CREATING_TIMEOUT";
            default -> "SYSTEM_ERROR";
        };
    }

    private static int statusCode(String status) {
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "CREATING" -> STATUS_CREATING;
            case "IN_PROGRESS" -> STATUS_IN_PROGRESS;
            case "COMPLETED" -> STATUS_COMPLETED;
            case "INCOMPLETE" -> STATUS_INCOMPLETE;
            case "EXPIRED" -> STATUS_EXPIRED;
            case "VOIDED" -> STATUS_VOIDED;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "status 取值不合法");
        };
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Idempotency-Key 必填");
        }
        try {
            UUID.fromString(key.trim());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Idempotency-Key 必须是 UUID");
        }
    }

    private static List<String> splitSuggestions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            if (!line.isBlank()) {
                items.add(line.trim());
            }
        }
        return items;
    }

    private static String normalizeContentType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            String normalized = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if (!normalized.equals("application/octet-stream")) {
                return normalized;
            }
        }
        if (filename == null) {
            return "";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return switch (lower.substring(dot + 1)) {
            case "webm" -> "audio/webm";
            case "ogg", "opus" -> "audio/ogg";
            case "mp4" -> "audio/mp4";
            case "m4a" -> "audio/m4a";
            case "wav" -> "audio/wav";
            default -> "";
        };
    }

    private static String serverTime() {
        return TimeFormats.rfc3339(java.time.Instant.now());
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

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        return null;
    }

    // ------------------------------------------------------------------ 内部记录

    private record SessionRow(
            long id,
            long studentId,
            long subjectId,
            String subjectName,
            long difficultyId,
            String difficultyCode,
            String difficultyName,
            long stageId,
            String stageName,
            int status,
            int currentRound,
            LocalDate quotaDate,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime expireAt,
            Integer voidReason
    ) {
    }

    private record Config(
            long subjectId,
            long difficultyId,
            String difficultyCode,
            String difficultyName,
            long stageId,
            String stageName,
            InterviewAiClient.Context context
    ) {
    }
}
