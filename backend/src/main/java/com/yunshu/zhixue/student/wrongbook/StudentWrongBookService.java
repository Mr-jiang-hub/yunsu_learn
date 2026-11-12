package com.yunshu.zhixue.student.wrongbook;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.student.wrongbook.dto.WrongQuestionDetail;
import com.yunshu.zhixue.student.wrongbook.dto.WrongQuestionPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * C 端错题本（04 §6.6）。只读：无手动移除接口，答对后由练习判分自动置 REMOVED。
 *
 * <p>列表按 `last_added_at DESC, question_id DESC` 排序，不含完整解析；
 * 详情含正确答案与解析，仅本人错题本内在册记录可访问。</p>
 */
@Service
public class StudentWrongBookService {

    private static final int WQ_IN_BOOK = 1;
    private static final int PAGE_MAX = 100;
    private static final int PREVIEW_LENGTH = 60;

    private static final String BASE_FROM =
            "FROM wrong_question wq JOIN question q ON q.id = wq.question_id "
                    + "JOIN stage st ON st.id = q.stage_id "
                    + "JOIN learning_path p ON p.id = st.path_id "
                    + "JOIN subject s ON s.id = p.subject_id "
                    + "WHERE wq.student_id = ? AND wq.status = 1 ";

    private final JdbcTemplate jdbcTemplate;

    public StudentWrongBookService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public WrongQuestionPage list(long studentId, int page, int size, Long subjectId, Long stageId) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), PAGE_MAX);

        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        if (subjectId != null) {
            where.append("AND p.subject_id = ? ");
            args.add(subjectId);
        }
        if (stageId != null) {
            where.append("AND q.stage_id = ? ");
            args.add(stageId);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + BASE_FROM + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add((long) (safePage - 1) * safeSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT wq.question_id, q.type, q.content, q.stage_id, st.name AS stage_name, "
                        + "p.subject_id, s.name AS subject_name, wq.last_added_at, wq.first_added_at "
                        + BASE_FROM + where
                        + "ORDER BY wq.last_added_at DESC, wq.question_id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());

        List<WrongQuestionPage.WrongQuestionItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(new WrongQuestionPage.WrongQuestionItem(
                    number(row.get("question_id")),
                    typeName(intValue(row.get("type"))),
                    preview(String.valueOf(row.get("content"))),
                    number(row.get("subject_id")),
                    String.valueOf(row.get("subject_name")),
                    number(row.get("stage_id")),
                    String.valueOf(row.get("stage_name")),
                    TimeFormats.rfc3339(row.get("last_added_at")),
                    TimeFormats.rfc3339(row.get("first_added_at"))));
        }

        long totalValue = total == null ? 0L : total;
        return new WrongQuestionPage(
                items, safePage, safeSize, totalValue,
                (long) safePage * safeSize < totalValue);
    }

    @Transactional(readOnly = true)
    public WrongQuestionDetail detail(long studentId, long questionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT wq.question_id, wq.last_wrong_answer_id, wq.last_added_at, wq.first_added_at, "
                        + "q.type, q.content, q.analysis, q.reference_answer "
                        + "FROM wrong_question wq JOIN question q ON q.id = wq.question_id "
                        + "WHERE wq.student_id = ? AND wq.question_id = ? AND wq.status = ?",
                studentId, questionId, WQ_IN_BOOK);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.WRONG_QUESTION_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);

        List<WrongQuestionDetail.OptionDetail> options = new ArrayList<>();
        List<Long> correctOptionIds = new ArrayList<>();
        for (Map<String, Object> option : jdbcTemplate.queryForList(
                "SELECT id, option_key, content, is_correct FROM question_option WHERE question_id = ? "
                        + "ORDER BY sort_no ASC, id ASC",
                questionId)) {
            boolean correct = intValue(option.get("is_correct")) == 1;
            options.add(new WrongQuestionDetail.OptionDetail(
                    number(option.get("id")),
                    String.valueOf(option.get("option_key")),
                    String.valueOf(option.get("content")),
                    correct));
            if (correct) {
                correctOptionIds.add(number(option.get("id")));
            }
        }

        List<String> scoringPoints = jdbcTemplate.queryForList(
                "SELECT point_text FROM question_scoring_point WHERE question_id = ? ORDER BY sort_no ASC",
                String.class, questionId);

        WrongQuestionDetail.MyLastAnswer lastAnswer = lastAnswer(row.get("last_wrong_answer_id"));

        return new WrongQuestionDetail(
                new WrongQuestionDetail.QuestionDetail(
                        questionId,
                        typeName(intValue(row.get("type"))),
                        String.valueOf(row.get("content")),
                        options),
                lastAnswer,
                correctOptionIds,
                (String) row.get("reference_answer"),
                String.valueOf(row.get("analysis")),
                scoringPoints,
                TimeFormats.rfc3339(row.get("last_added_at")),
                TimeFormats.rfc3339(row.get("first_added_at")));
    }

    private WrongQuestionDetail.MyLastAnswer lastAnswer(Object answerId) {
        if (answerId == null) {
            return new WrongQuestionDetail.MyLastAnswer(List.of(), null);
        }
        long id = ((Number) answerId).longValue();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT answer_text FROM answer WHERE id = ?", id);
        String text = rows.isEmpty() ? null : (String) rows.get(0).get("answer_text");
        List<Long> selected = jdbcTemplate.queryForList(
                "SELECT option_id FROM answer_option WHERE answer_id = ? ORDER BY id ASC", Long.class, id);
        return new WrongQuestionDetail.MyLastAnswer(selected, text);
    }

    private static String preview(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= PREVIEW_LENGTH
                ? normalized : normalized.substring(0, PREVIEW_LENGTH) + "…";
    }

    private static String typeName(int type) {
        return switch (type) {
            case 1 -> "TRUE_FALSE";
            case 2 -> "SINGLE_CHOICE";
            case 3 -> "MULTIPLE_CHOICE";
            case 4 -> "SHORT_ANSWER";
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
}
