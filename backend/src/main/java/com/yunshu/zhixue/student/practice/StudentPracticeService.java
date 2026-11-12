package com.yunshu.zhixue.student.practice;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.student.practice.dto.JudgeView;
import com.yunshu.zhixue.student.practice.dto.PracticeDetailResponse;
import com.yunshu.zhixue.student.practice.dto.PracticeResultResponse;
import com.yunshu.zhixue.student.practice.dto.PracticeStartResponse;
import com.yunshu.zhixue.student.practice.dto.PracticeSubmitResponse;
import com.yunshu.zhixue.student.practice.dto.SelfAssessmentRequest;
import com.yunshu.zhixue.student.practice.dto.SubmitAnswerRequest;
import com.yunshu.zhixue.student.practice.dto.SubmitAnswerResponse;
import com.yunshu.zhixue.student.practice.dto.WrongRetryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C 端阶段练习与错题重练（04 §6.5）。
 *
 * <p>关键约束：</p>
 * <ol>
 *   <li>练习题目集合没有快照表（03-Q35 已接受该风险），按"当前启用题目"实时计算；
 *       错题重练取"本次已答题目 ∪ 当前在册错题"，保证已答题不会因移出错题本而消失。</li>
 *   <li>提交答案分两个短事务：事务 A 只落原始答案并标"待判分"，事务外调大模型，
 *       事务 B 回写判分与错题本 —— rules 禁止在事务内调用大模型。</li>
 *   <li>AI 失败/超时降级为学员自评（`is_degraded=1`、`judge_status=3`），绝不当 0 分处理。</li>
 *   <li>幂等由 `uk_answer(practice_id, question_id)` 兜底：重复提交内容一致返回首次结果，
 *       内容不同返回 30025。</li>
 * </ol>
 */
@Service
public class StudentPracticeService {

    private static final Logger log = LoggerFactory.getLogger(StudentPracticeService.class);

    private static final int TYPE_TRUE_FALSE = 1;
    private static final int TYPE_SINGLE = 2;
    private static final int TYPE_MULTIPLE = 3;
    private static final int TYPE_SHORT_ANSWER = 4;

    private static final int PRACTICE_STAGE = 1;
    private static final int PRACTICE_WRONG_RETRY = 2;

    private static final int PRACTICE_IN_PROGRESS = 1;
    private static final int PRACTICE_FINISHED = 2;
    private static final int PRACTICE_VOIDED = 3;

    static final int JUDGE_DONE = 2;
    static final int JUDGE_SELF_PENDING = 3;
    static final int JUDGE_SELF_DONE = 4;

    private static final int STATUS_ENABLED = 1;
    private static final int WQ_IN_BOOK = 1;
    private static final int WQ_REMOVED = 2;

    private static final int EXPIRE_DAYS = 7;
    private static final double SHORT_ANSWER_PASS = 60d;

    private static final String SELECT_PRACTICE =
            "SELECT p.id, p.type, p.status, p.stage_id, st.name AS stage_name, p.started_at, "
                    + "p.finished_at, p.expire_at, p.question_count, p.correct_count, p.correct_rate "
                    + "FROM practice p LEFT JOIN stage st ON st.id = p.stage_id "
                    + "WHERE p.id = ? AND p.student_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final ShortAnswerJudge shortAnswerJudge;

    public StudentPracticeService(JdbcTemplate jdbcTemplate,
                                  PlatformTransactionManager txManager,
                                  ShortAnswerJudge shortAnswerJudge) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(txManager);
        this.shortAnswerJudge = shortAnswerJudge;
    }

    // ------------------------------------------------------------------ 创建

    /** 创建/复用阶段练习（04 §6.5.1） */
    public PracticeStartResponse startStagePractice(long stageId, long studentId) {
        Map<String, Object> stage = requireStage(stageId);
        List<Long> questionIds = jdbcTemplate.queryForList(
                "SELECT id FROM question WHERE stage_id = ? AND status = 1 ORDER BY id ASC",
                Long.class, stageId);
        if (questionIds.isEmpty()) {
            throw new BizException(ErrorCode.STAGE_NO_QUESTIONS);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Map<String, Object>> running = jdbcTemplate.queryForList(
                "SELECT id FROM practice WHERE student_id = ? AND stage_id = ? AND type = ? AND status = ? "
                        + "AND expire_at > ? ORDER BY id DESC LIMIT 1",
                studentId, stageId, PRACTICE_STAGE, PRACTICE_IN_PROGRESS, now);
        if (!running.isEmpty()) {
            long practiceId = ((Number) running.get(0).get("id")).longValue();
            return buildStartResponse(practiceId, studentId, true);
        }

        long practiceId = txTemplate.execute(status -> insertPractice(
                studentId, number(stage.get("id")), PRACTICE_STAGE, questionIds, now));
        return buildStartResponse(practiceId, studentId, false);
    }

    /** 创建/复用错题重练（04 §6.5.2）：不计入掌握度与完成度 */
    public PracticeStartResponse startWrongRetry(long studentId, WrongRetryRequest request) {
        requireFilterValid(request.subjectId(), request.stageId());
        List<Long> questionIds = wrongQuestionIds(studentId, request.subjectId(), request.stageId());
        if (questionIds.isEmpty()) {
            throw new BizException(ErrorCode.WRONG_BOOK_EMPTY);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Map<String, Object>> running = jdbcTemplate.queryForList(
                "SELECT id FROM practice WHERE student_id = ? AND type = ? AND status = ? AND expire_at > ? "
                        + "ORDER BY id DESC LIMIT 1",
                studentId, PRACTICE_WRONG_RETRY, PRACTICE_IN_PROGRESS, now);
        if (!running.isEmpty()) {
            long practiceId = ((Number) running.get(0).get("id")).longValue();
            return buildStartResponse(practiceId, studentId, true);
        }

        long practiceId = txTemplate.execute(status ->
                insertPractice(studentId, null, PRACTICE_WRONG_RETRY, questionIds, now));
        return buildStartResponse(practiceId, studentId, false);
    }

    /**
     * 创建练习并落题目快照（05 G-03）。
     *
     * <p>快照与练习行同一事务写入：题目集合一旦固定就不再随题库增删/停用变化，
     * 保证 7 天续答期内"同一练习可复现"。写入失败会连带回滚练习行，不会出现有练习无题目的半成品。</p>
     */
    private long insertPractice(long studentId, Long stageId, int type, List<Long> questionIds, LocalDateTime now) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO practice (student_id, stage_id, type, status, started_at, expire_at, question_count) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, studentId);
            if (stageId == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, stageId);
            }
            ps.setInt(3, type);
            ps.setInt(4, PRACTICE_IN_PROGRESS);
            ps.setObject(5, now);
            ps.setObject(6, now.plusDays(EXPIRE_DAYS));
            ps.setInt(7, questionIds.size());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "创建练习未取得主键");
        }
        long practiceId = key.longValue();
        saveSnapshot(practiceId, questionIds);
        return practiceId;
    }

    /** 题目快照按 sort_no 固定题序；批量写入，题量上限 1000 与导入上限一致 */
    private void saveSnapshot(long practiceId, List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO practice_question (practice_id, question_id, sort_no) VALUES (?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setLong(1, practiceId);
                        ps.setLong(2, questionIds.get(i));
                        ps.setInt(3, i + 1);
                    }

                    @Override
                    public int getBatchSize() {
                        return questionIds.size();
                    }
                });
    }

    private PracticeStartResponse buildStartResponse(long practiceId, long studentId, boolean reused) {
        PracticeRow practice = loadPractice(practiceId, studentId);
        List<Long> questionIds = questionIdsOf(practice, studentId);
        Set<Long> answeredIds = answeredQuestionIds(practiceId);
        Long nextId = firstUnanswered(questionIds, answeredIds);
        PracticeStartResponse.QuestionView first = nextId == null ? null : questionView(nextId);

        return new PracticeStartResponse(
                practiceId,
                practice.type() == PRACTICE_STAGE ? "STAGE" : "WRONG_RETRY",
                statusText(practice.status()),
                questionIds.size(),
                answeredIds.size(),
                TimeFormats.rfc3339(practice.expireAt()),
                reused,
                first);
    }

    // ------------------------------------------------------------------ 详情

    public PracticeDetailResponse detail(long practiceId, long studentId) {
        PracticeRow practice = loadPractice(practiceId, studentId);
        PracticeRow effective = expireIfNeeded(practice);

        List<Long> questionIds = questionIdsOf(effective, studentId);
        Set<Long> answeredIds = answeredQuestionIds(practiceId);
        Long currentId = effective.status() == PRACTICE_IN_PROGRESS
                ? firstUnanswered(questionIds, answeredIds) : null;

        List<PracticeDetailResponse.AnsweredItem> answered = new ArrayList<>(answeredIds.size());
        for (Long questionId : questionIds) {
            if (!answeredIds.contains(questionId)) {
                continue;
            }
            JudgeView judge = judgeOf(practiceId, questionId);
            answered.add(new PracticeDetailResponse.AnsweredItem(
                    questionId, myAnswerOf(practiceId, questionId), judge));
        }

        int answeredCount = answeredIds.size();
        int remaining = Math.max(0, questionIds.size() - answeredCount);
        PracticeDetailResponse.PracticeInfo info = new PracticeDetailResponse.PracticeInfo(
                practiceId,
                effective.type() == PRACTICE_STAGE ? "STAGE" : "WRONG_RETRY",
                statusText(effective.status()),
                effective.stageId(),
                effective.stageName(),
                TimeFormats.rfc3339(effective.startedAt()),
                TimeFormats.rfc3339(effective.expireAt()),
                questionIds.size(),
                answeredCount,
                remaining,
                Math.min(answeredCount + 1, questionIds.size() + 1));

        return new PracticeDetailResponse(
                info,
                currentId == null ? null : questionView(currentId),
                answered,
                effective.status() == PRACTICE_IN_PROGRESS && remaining == 0 && !questionIds.isEmpty());
    }

    // ------------------------------------------------------------------ 作答

    /**
     * 提交单题作答（04 §6.5.4）。事务 A 落原始答案 → 事务外判分 → 事务 B 回写。
     */
    public SubmitAnswerResponse submitAnswer(long practiceId, long studentId, SubmitAnswerRequest request) {
        PreparedAnswer prepared = txTemplate.execute(status ->
                prepareAnswer(practiceId, studentId, request));
        if (prepared == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "提交作答失败");
        }
        if (prepared.replayed()) {
            return buildAnswerResponse(practiceId, studentId, prepared.questionId(), true);
        }

        JudgeOutcome outcome = judge(prepared);
        txTemplate.executeWithoutResult(status -> persistJudge(prepared, outcome));

        return buildAnswerResponse(practiceId, studentId, prepared.questionId(), false);
    }

    private PreparedAnswer prepareAnswer(long practiceId, long studentId, SubmitAnswerRequest request) {
        PracticeRow practice = expireIfNeeded(loadPractice(practiceId, studentId));
        if (practice.status() == PRACTICE_FINISHED) {
            throw new BizException(ErrorCode.PRACTICE_FINISHED);
        }
        if (practice.status() == PRACTICE_VOIDED) {
            throw new BizException(ErrorCode.PRACTICE_VOIDED);
        }

        long questionId = request.questionId();
        List<Long> questionIds = questionIdsOf(practice, studentId);
        if (!questionIds.contains(questionId)) {
            throw new BizException(ErrorCode.QUESTION_NOT_IN_PRACTICE);
        }

        Set<Long> answered = answeredQuestionIds(practiceId);
        Map<String, Object> question = requireQuestion(questionId);
        int type = intValue(question.get("type"));
        List<Long> correctOptionIds = correctOptionIds(questionId);

        List<Long> selected = request.selectedOptionIds() == null
                ? List.of() : new ArrayList<>(new LinkedHashSet<>(request.selectedOptionIds()));
        String answerText = request.answerText() == null ? null : request.answerText().trim();

        if (answered.contains(questionId)) {
            if (sameAnswer(practiceId, questionId, type, selected, answerText)) {
                return new PreparedAnswer(0L, questionId, type, 0, true, null, List.of(), List.of(), null);
            }
            throw new BizException(ErrorCode.ANSWER_IMMUTABLE);
        }

        if (type == TYPE_SHORT_ANSWER) {
            if (answerText == null || answerText.isEmpty() || answerText.length() > 2000) {
                throw new BizException(ErrorCode.PARAM_INVALID, "简答题答案需为 1–2000 个字符");
            }
        } else {
            if (selected.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择答案选项");
            }
            if (type != TYPE_MULTIPLE && selected.size() != 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该题型只能选择一个选项");
            }
            List<Long> validOptionIds = jdbcTemplate.queryForList(
                    "SELECT id FROM question_option WHERE question_id = ?", Long.class, questionId);
            if (!validOptionIds.containsAll(selected)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "选项不属于该题目");
            }
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO answer (practice_id, question_id, answer_text, judge_status) VALUES (?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, practiceId);
            ps.setLong(2, questionId);
            ps.setString(3, type == TYPE_SHORT_ANSWER ? answerText : null);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "保存作答未取得主键");
        }
        long answerId = key.longValue();

        for (Long optionId : selected) {
            jdbcTemplate.update(
                    "INSERT INTO answer_option (answer_id, option_id) VALUES (?, ?) "
                            + "ON DUPLICATE KEY UPDATE option_id = option_id",
                    answerId, optionId);
        }

        List<String> scoringPoints = jdbcTemplate.queryForList(
                "SELECT point_text FROM question_scoring_point WHERE question_id = ? ORDER BY sort_no ASC",
                String.class, questionId);

        return new PreparedAnswer(
                answerId,
                questionId,
                type,
                practiceId,
                false,
                answerText,
                selected,
                correctOptionIds,
                new JudgeInput(
                        String.valueOf(question.get("content")),
                        scoringPoints,
                        (String) question.get("reference_answer"),
                        String.valueOf(question.get("analysis")),
                        now));
    }

    private JudgeOutcome judge(PreparedAnswer prepared) {
        JudgeInput input = prepared.input();
        if (input == null) {
            return JudgeOutcome.replay();
        }
        if (prepared.type() == TYPE_SHORT_ANSWER) {
            ShortAnswerJudge.Outcome outcome = shortAnswerJudge.judge(
                    input.content(), input.scoringPoints(), input.referenceAnswer(), prepared.answerText());
            List<JudgeOutcome.Hit> hits = new ArrayList<>(outcome.hitPoints().size());
            for (ShortAnswerJudge.Outcome.HitPoint point : outcome.hitPoints()) {
                hits.add(new JudgeOutcome.Hit(point.point(), point.hit()));
            }
            return new JudgeOutcome(
                    outcome.degraded(),
                    outcome.score(),
                    outcome.correct(),
                    outcome.feedback(),
                    hits);
        }
        Set<Long> selected = new HashSet<>(prepared.selectedOptionIds());
        Set<Long> correct = new HashSet<>(prepared.correctOptionIds());
        boolean isCorrect = !selected.isEmpty() && selected.equals(correct);
        return new JudgeOutcome(false, isCorrect ? 100d : 0d, isCorrect, "", List.of());
    }

    private void persistJudge(PreparedAnswer prepared, JudgeOutcome outcome) {
        LocalDateTime judgedAt = LocalDateTime.now(ZoneOffset.UTC);
        if (outcome.degraded()) {
            // 降级：不写分数、不写对错，等学员自评
            jdbcTemplate.update(
                    "UPDATE answer SET judge_status = ?, is_degraded = 1, feedback = ?, judged_at = NULL "
                            + "WHERE id = ?",
                    JUDGE_SELF_PENDING, outcome.feedback(), prepared.answerId());
            replaceScoringPoints(prepared, outcome.hits());
            return;
        }

        jdbcTemplate.update(
                "UPDATE answer SET judge_status = ?, is_degraded = 0, is_correct = ?, score = ?, "
                        + "feedback = ?, judged_at = ? WHERE id = ?",
                JUDGE_DONE, Boolean.TRUE.equals(outcome.correct()) ? 1 : 0, outcome.score(),
                outcome.feedback(), judgedAt, prepared.answerId());
        replaceScoringPoints(prepared, outcome.hits());
        syncWrongBook(prepared.practiceId(), prepared.questionId(), prepared.answerId(),
                Boolean.TRUE.equals(outcome.correct()), judgedAt);
    }

    private void replaceScoringPoints(PreparedAnswer prepared, List<JudgeOutcome.Hit> hits) {
        if (hits.isEmpty() || prepared.answerId() <= 0) {
            return;
        }
        long answerId = prepared.answerId();
        jdbcTemplate.update("DELETE FROM answer_scoring_point WHERE answer_id = ?", answerId);
        for (JudgeOutcome.Hit hit : hits) {
            List<Long> pointIds = jdbcTemplate.queryForList(
                    "SELECT id FROM question_scoring_point WHERE question_id = ? AND point_text = ?",
                    Long.class, prepared.questionId(), hit.point());
            if (pointIds.isEmpty()) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO answer_scoring_point (answer_id, scoring_point_id, is_hit) VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE is_hit = VALUES(is_hit)",
                    answerId, pointIds.get(0), hit.hit() ? 1 : 0);
        }
    }

    /** 答错入册/刷新，答对移出（04 §6.6 规则 1）；错题重练同样适用 */
    private void syncWrongBook(long practiceId, long questionId, long answerId, boolean isCorrect, LocalDateTime now) {
        if (isCorrect) {
            jdbcTemplate.update(
                    "UPDATE wrong_question SET status = ?, removed_at = ? "
                            + "WHERE student_id = (SELECT student_id FROM practice WHERE id = ?) "
                            + "AND question_id = ? AND status = ?",
                    WQ_REMOVED, now, practiceId, questionId, WQ_IN_BOOK);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO wrong_question (student_id, question_id, status, first_added_at, last_added_at, "
                        + "last_wrong_answer_id) SELECT student_id, ?, ?, ?, ?, ? FROM practice WHERE id = ? "
                        + "ON DUPLICATE KEY UPDATE status = VALUES(status), last_added_at = VALUES(last_added_at), "
                        + "last_wrong_answer_id = VALUES(last_wrong_answer_id), removed_at = NULL",
                questionId, WQ_IN_BOOK, now, now, answerId, practiceId);
    }

    // ------------------------------------------------------------------ 自评

    /** 简答降级后的学员自评（04 §6.5.5）：重复自评返回首次结果 */
    public JudgeView selfAssess(long practiceId, long questionId, long studentId, SelfAssessmentRequest request) {
        return txTemplate.execute(status -> {
            PracticeRow practice = loadPractice(practiceId, studentId);
            Map<String, Object> answer = requireAnswer(practiceId, questionId);
            int judgeStatus = intValue(answer.get("judge_status"));
            if (judgeStatus == JUDGE_SELF_DONE) {
                return judgeOf(practiceId, questionId);
            }
            if (judgeStatus != JUDGE_SELF_PENDING) {
                throw new BizException(ErrorCode.SELF_ASSESSMENT_INVALID);
            }
            boolean isCorrect = Boolean.TRUE.equals(request.isCorrect());
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            jdbcTemplate.update(
                    "UPDATE answer SET judge_status = ?, is_correct = ?, judged_at = ? WHERE id = ?",
                    JUDGE_SELF_DONE, isCorrect ? 1 : 0, now,
                    ((Number) answer.get("id")).longValue());
            syncWrongBook(practiceId, questionId, ((Number) answer.get("id")).longValue(), isCorrect, now);
            return judgeOf(practiceId, questionId);
        });
    }

    // ------------------------------------------------------------------ 提交与结果

    public PracticeSubmitResponse submit(long practiceId, long studentId) {
        return txTemplate.execute(status -> {
            PracticeRow practice = expireIfNeeded(loadPractice(practiceId, studentId));
            if (practice.status() == PRACTICE_FINISHED) {
                return submitResponse(practice);
            }
            if (practice.status() == PRACTICE_VOIDED) {
                throw new BizException(ErrorCode.PRACTICE_VOIDED);
            }

            List<Long> questionIds = questionIdsOf(practice, studentId);
            Set<Long> answered = answeredQuestionIds(practiceId);
            int unanswered = questionIds.size() - answered.size();
            if (unanswered > 0) {
                throw new BizException(ErrorCode.PRACTICE_UNANSWERED,
                        "还有 " + unanswered + " 道题未作答", Map.of("unansweredCount", unanswered));
            }

            int correctCount = intValue(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM answer WHERE practice_id = ? AND is_correct = 1",
                    Integer.class, practiceId));
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            double correctRate = rate(correctCount, questionIds.size());
            jdbcTemplate.update(
                    "UPDATE practice SET status = ?, finished_at = ?, question_count = ?, correct_count = ?, "
                            + "correct_rate = ? WHERE id = ?",
                    PRACTICE_FINISHED, now, questionIds.size(), correctCount, correctRate, practiceId);

            PracticeRow finished = loadPractice(practiceId, studentId);
            log.debug("练习提交 practiceId={} correct={}/{}", practiceId, correctCount, questionIds.size());
            return submitResponse(finished);
        });
    }

    private PracticeSubmitResponse submitResponse(PracticeRow practice) {
        boolean masteryEligible = practice.type() == PRACTICE_STAGE;
        double correctRate = practice.correctRate() == null ? 0d : practice.correctRate();
        long duration = practice.finishedAt() == null || practice.startedAt() == null
                ? 0L : Math.max(0L, Duration.between(practice.startedAt(), practice.finishedAt()).getSeconds());
        return new PracticeSubmitResponse(
                practice.id(),
                statusText(practice.status()),
                practice.questionCount(),
                intValue(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE practice_id = ?", Integer.class, practice.id())),
                practice.correctCount(),
                correctRate,
                duration,
                masteryEligible ? correctRate : null,
                masteryEligible,
                TimeFormats.rfc3339(practice.finishedAt()));
    }

    public PracticeResultResponse result(long practiceId, long studentId) {
        PracticeRow practice = loadPractice(practiceId, studentId);
        if (practice.status() == PRACTICE_VOIDED) {
            throw new BizException(ErrorCode.PRACTICE_VOIDED);
        }
        if (practice.status() != PRACTICE_FINISHED) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FINISHED);
        }

        List<Long> questionIds = questionIdsOf(practice, studentId);
        List<PracticeResultResponse.WrongItem> wrong = new ArrayList<>();
        for (Long questionId : questionIds) {
            JudgeView judge = judgeOf(practiceId, questionId);
            if (judge == null || Boolean.TRUE.equals(judge.isCorrect())) {
                continue;
            }
            Map<String, Object> question = requireQuestion(questionId);
            wrong.add(new PracticeResultResponse.WrongItem(
                    questionId,
                    typeName(intValue(question.get("type"))),
                    String.valueOf(question.get("content")),
                    myAnswerOf(practiceId, questionId),
                    judge == null ? List.of() : judge.correctOptionIds(),
                    (String) question.get("reference_answer"),
                    String.valueOf(question.get("analysis")),
                    judge == null ? null : judge.score(),
                    judge != null && judge.degraded()));
        }

        boolean masteryEligible = practice.type() == PRACTICE_STAGE;
        double correctRate = practice.correctRate() == null ? 0d : practice.correctRate();
        long duration = practice.finishedAt() == null || practice.startedAt() == null
                ? 0L : Math.max(0L, Duration.between(practice.startedAt(), practice.finishedAt()).getSeconds());

        return new PracticeResultResponse(
                practiceId,
                masteryEligible ? "STAGE" : "WRONG_RETRY",
                statusText(practice.status()),
                practice.stageId(),
                practice.stageName(),
                practice.questionCount(),
                intValue(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE practice_id = ?", Integer.class, practiceId)),
                practice.correctCount(),
                correctRate,
                masteryEligible ? correctRate : null,
                masteryEligible,
                duration,
                TimeFormats.rfc3339(practice.finishedAt()),
                wrong);
    }

    // ------------------------------------------------------------------ 查询辅助

    private PracticeRow loadPractice(long practiceId, long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_PRACTICE, practiceId, studentId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);
        return new PracticeRow(
                number(row.get("id")),
                intValue(row.get("type")),
                intValue(row.get("status")),
                row.get("stage_id") == null ? null : number(row.get("stage_id")),
                (String) row.get("stage_name"),
                toLocalDateTime(row.get("started_at")),
                toLocalDateTime(row.get("finished_at")),
                toLocalDateTime(row.get("expire_at")),
                intValue(row.get("question_count")),
                intValue(row.get("correct_count")),
                row.get("correct_rate") == null ? null : ((Number) row.get("correct_rate")).doubleValue());
    }

    /** 过期即作废（04 §6.5 规则：超期练习置 VOIDED） */
    private PracticeRow expireIfNeeded(PracticeRow practice) {
        if (practice.status() != PRACTICE_IN_PROGRESS) {
            return practice;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (practice.expireAt() == null || !practice.expireAt().isBefore(now)) {
            return practice;
        }
        jdbcTemplate.update("UPDATE practice SET status = ?, finished_at = ? WHERE id = ?",
                PRACTICE_VOIDED, now, practice.id());
        return new PracticeRow(practice.id(), practice.type(), PRACTICE_VOIDED, practice.stageId(),
                practice.stageName(), practice.startedAt(), now, practice.expireAt(),
                practice.questionCount(), practice.correctCount(), practice.correctRate());
    }

    /**
     * 本次练习的题目集合（05 G-03）。
     *
     * <p>优先读创建时落下的 `practice_question` 快照——题库后续增删/停用都不影响，
     * 这是"续答可复现"的保证。快照为空只可能是 V5 之前创建的历史练习，
     * 此时回退到按 stage_id / 错题本推导，行为与迁移前一致。</p>
     */
    private List<Long> questionIdsOf(PracticeRow practice, long studentId) {
        List<Long> snapshot = jdbcTemplate.queryForList(
                "SELECT question_id FROM practice_question WHERE practice_id = ? ORDER BY sort_no ASC",
                Long.class, practice.id());
        if (!snapshot.isEmpty()) {
            return snapshot;
        }
        if (practice.type() == PRACTICE_STAGE && practice.stageId() != null) {
            return jdbcTemplate.queryForList(
                    "SELECT id FROM question WHERE stage_id = ? AND status = 1 ORDER BY id ASC",
                    Long.class, practice.stageId());
        }
        List<Long> ids = new ArrayList<>(jdbcTemplate.queryForList(
                "SELECT question_id FROM answer WHERE practice_id = ? ORDER BY question_id ASC",
                Long.class, practice.id()));
        for (Long id : wrongQuestionIds(studentId, null, null)) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** 筛选范围必须存在且可用，否则 30031（04 §6.5.2） */
    private void requireFilterValid(Long subjectId, Long stageId) {
        if (subjectId != null) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM subject WHERE id = ?", Integer.class, subjectId);
            if (count == null || count == 0) {
                throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "筛选学科不存在或不可用");
            }
        }
        if (stageId != null) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM stage WHERE id = ?", Integer.class, stageId);
            if (count == null || count == 0) {
                throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "筛选阶段不存在或不可用");
            }
        }
    }

    private List<Long> wrongQuestionIds(long studentId, Long subjectId, Long stageId) {        StringBuilder sql = new StringBuilder(
                "SELECT wq.question_id FROM wrong_question wq "
                        + "JOIN question q ON q.id = wq.question_id "
                        + "JOIN stage st ON st.id = q.stage_id "
                        + "JOIN learning_path p ON p.id = st.path_id "
                        + "WHERE wq.student_id = ? AND wq.status = 1 AND q.status = 1 ");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        if (stageId != null) {
            sql.append("AND q.stage_id = ? ");
            args.add(stageId);
        }
        if (subjectId != null) {
            sql.append("AND p.subject_id = ? ");
            args.add(subjectId);
        }
        sql.append("ORDER BY wq.last_added_at DESC, wq.question_id DESC");
        return jdbcTemplate.queryForList(sql.toString(), Long.class, args.toArray());
    }

    private Set<Long> answeredQuestionIds(long practiceId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT question_id FROM answer WHERE practice_id = ?", Long.class, practiceId));
    }

    private static Long firstUnanswered(List<Long> questionIds, Set<Long> answered) {
        for (Long id : questionIds) {
            if (!answered.contains(id)) {
                return id;
            }
        }
        return null;
    }

    private PracticeStartResponse.QuestionView questionView(long questionId) {
        Map<String, Object> question = requireQuestion(questionId);
        List<Map<String, Object>> options = jdbcTemplate.queryForList(
                "SELECT id, content FROM question_option WHERE question_id = ? ORDER BY sort_no ASC, id ASC",
                questionId);
        List<PracticeStartResponse.OptionView> views = new ArrayList<>(options.size());
        for (Map<String, Object> option : options) {
            views.add(new PracticeStartResponse.OptionView(
                    number(option.get("id")), String.valueOf(option.get("content"))));
        }
        return new PracticeStartResponse.QuestionView(
                questionId,
                typeName(intValue(question.get("type"))),
                String.valueOf(question.get("content")),
                views);
    }

    private JudgeView judgeOf(long practiceId, long questionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT a.id, a.question_id, a.judge_status, a.is_correct, a.score, a.feedback, "
                        + "a.is_degraded, a.judged_at, q.analysis, q.reference_answer, q.type "
                        + "FROM answer a JOIN question q ON q.id = a.question_id "
                        + "WHERE a.practice_id = ? AND a.question_id = ?",
                practiceId, questionId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        int judgeStatus = intValue(row.get("judge_status"));
        boolean degraded = intValue(row.get("is_degraded")) == 1;
        boolean scored = judgeStatus == JUDGE_DONE || judgeStatus == JUDGE_SELF_DONE;
        boolean shortAnswer = intValue(row.get("type")) == TYPE_SHORT_ANSWER;
        List<Long> correctOptions = shortAnswer ? List.of() : correctOptionIds(questionId);

        List<JudgeView.HitPoint> hits = new ArrayList<>();
        if (shortAnswer) {
            List<Map<String, Object>> points = jdbcTemplate.queryForList(
                    "SELECT sp.point_text, asp.is_hit FROM answer_scoring_point asp "
                            + "JOIN question_scoring_point sp ON sp.id = asp.scoring_point_id "
                            + "WHERE asp.answer_id = ? ORDER BY sp.sort_no ASC",
                    ((Number) row.get("id")).longValue());
            for (Map<String, Object> point : points) {
                hits.add(new JudgeView.HitPoint(
                        String.valueOf(point.get("point_text")), intValue(point.get("is_hit")) == 1));
            }
        }

        return new JudgeView(
                questionId,
                judgeStatusText(judgeStatus),
                scored,
                degraded,
                row.get("is_correct") == null ? null : intValue(row.get("is_correct")) == 1,
                row.get("score") == null ? null : ((Number) row.get("score")).doubleValue(),
                correctOptions,
                row.get("analysis") == null ? "" : String.valueOf(row.get("analysis")),
                shortAnswer ? (String) row.get("reference_answer") : null,
                hits,
                TimeFormats.rfc3339(row.get("judged_at")));
    }

    private PracticeDetailResponse.MyAnswer myAnswerOf(long practiceId, long questionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, answer_text FROM answer WHERE practice_id = ? AND question_id = ?",
                practiceId, questionId);
        if (rows.isEmpty()) {
            return new PracticeDetailResponse.MyAnswer(List.of(), null);
        }
        long answerId = ((Number) rows.get(0).get("id")).longValue();
        List<Long> selected = jdbcTemplate.queryForList(
                "SELECT option_id FROM answer_option WHERE answer_id = ? ORDER BY id ASC",
                Long.class, answerId);
        return new PracticeDetailResponse.MyAnswer(selected, (String) rows.get(0).get("answer_text"));
    }

    private boolean sameAnswer(long practiceId, long questionId, int type, List<Long> selected, String answerText) {
        PracticeDetailResponse.MyAnswer existing = myAnswerOf(practiceId, questionId);
        if (type == TYPE_SHORT_ANSWER) {
            String existingText = existing.answerText() == null ? "" : existing.answerText().trim();
            return existingText.equals(answerText == null ? "" : answerText);
        }
        return new HashSet<>(existing.selectedOptionIds()).equals(new HashSet<>(selected));
    }

    private SubmitAnswerResponse buildAnswerResponse(long practiceId, long studentId, long questionId,
                                                     boolean replayed) {
        PracticeRow practice = loadPractice(practiceId, studentId);
        List<Long> questionIds = questionIdsOf(practice, studentId);
        Set<Long> answered = answeredQuestionIds(practiceId);
        Long nextId = firstUnanswered(questionIds, answered);
        int remaining = Math.max(0, questionIds.size() - answered.size());

        return new SubmitAnswerResponse(
                judgeOf(practiceId, questionId),
                new SubmitAnswerResponse.Progress(
                        answered.size(),
                        remaining,
                        Math.min(answered.size() + 1, questionIds.size() + 1),
                        statusText(practice.status())),
                nextId == null ? null : questionView(nextId),
                replayed);
    }

    private Map<String, Object> requireAnswer(long practiceId, long questionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, judge_status FROM answer WHERE practice_id = ? AND question_id = ?",
                practiceId, questionId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND, "该题尚未作答");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireQuestion(long questionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, type, content, analysis, reference_answer FROM question WHERE id = ?", questionId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND, "题目不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireStage(long stageId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT st.id, st.status, p.status AS path_status, s.status AS subject_status "
                        + "FROM stage st JOIN learning_path p ON p.id = st.path_id "
                        + "JOIN subject s ON s.id = p.subject_id WHERE st.id = ?",
                stageId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND, "阶段不存在或无权访问");
        }
        Map<String, Object> row = rows.get(0);
        if (intValue(row.get("status")) != STATUS_ENABLED
                || intValue(row.get("path_status")) != STATUS_ENABLED
                || intValue(row.get("subject_status")) != STATUS_ENABLED) {
            throw new BizException(ErrorCode.CONTENT_UNAVAILABLE, "该阶段暂不可练习");
        }
        return row;
    }

    private List<Long> correctOptionIds(long questionId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM question_option WHERE question_id = ? AND is_correct = 1 "
                        + "ORDER BY sort_no ASC, id ASC",
                Long.class, questionId);
    }

    // ------------------------------------------------------------------ 转换

    private static String statusText(int status) {
        return switch (status) {
            case PRACTICE_FINISHED -> "FINISHED";
            case PRACTICE_VOIDED -> "VOIDED";
            default -> "IN_PROGRESS";
        };
    }

    private static String judgeStatusText(int status) {
        return switch (status) {
            case JUDGE_DONE -> "JUDGED";
            case JUDGE_SELF_PENDING -> "PENDING_SELF_ASSESSMENT";
            case JUDGE_SELF_DONE -> "SELF_ASSESSED";
            default -> "PENDING";
        };
    }

    private static String typeName(int type) {
        return switch (type) {
            case TYPE_TRUE_FALSE -> "TRUE_FALSE";
            case TYPE_SINGLE -> "SINGLE_CHOICE";
            case TYPE_MULTIPLE -> "MULTIPLE_CHOICE";
            case TYPE_SHORT_ANSWER -> "SHORT_ANSWER";
            default -> "UNKNOWN";
        };
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

    private static double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return Math.round(numerator * 10000d / denominator) / 100d;
    }

    // ------------------------------------------------------------------ 内部记录

    private record PracticeRow(
            long id,
            int type,
            int status,
            Long stageId,
            String stageName,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime expireAt,
            int questionCount,
            int correctCount,
            Double correctRate
    ) {
    }

    private record JudgeInput(
            String content,
            List<String> scoringPoints,
            String referenceAnswer,
            String analysis,
            LocalDateTime answeredAt
    ) {
    }

    private record PreparedAnswer(
            long answerId,
            long questionId,
            int type,
            long practiceId,
            boolean replayed,
            String answerText,
            List<Long> selectedOptionIds,
            List<Long> correctOptionIds,
            JudgeInput input
    ) {
    }

    private record JudgeOutcome(
            boolean degraded,
            Double score,
            Boolean correct,
            String feedback,
            List<Hit> hits
    ) {

        static JudgeOutcome replay() {
            return new JudgeOutcome(false, null, null, "", List.of());
        }

        record Hit(String point, boolean hit) {
        }
    }
}
