package com.yunshu.zhixue.admin.question;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.admin.question.dto.ImportStartResponse;
import com.yunshu.zhixue.admin.question.dto.ImportTaskResponse;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.excel.SimpleXlsx;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.TimeFormats;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 题库批量导入（04 §6.10.4 / §8.6）。
 *
 * <p>语义：先做文件级校验（类型/大小/行数），落一条 {@code import_job=PROCESSING} 后立刻返回 taskId，
 * 真正的解析与写库在一个独立线程里跑完。任一行失败即整批回滚，任务置 {@code FAILED}，
 * 逐行错误写 {@code import_job_error}，行号用 Excel 真实行号（表头是第 1 行）。</p>
 *
 * <p>xlsx 读写走自带的 {@link SimpleXlsx}（不引入 POI），模板固定 14 列。</p>
 */
@Service
public class QuestionImportService {

    private static final Logger log = LoggerFactory.getLogger(QuestionImportService.class);

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 1000;
    private static final int MAX_OPTION_COLUMNS = 6;

    private static final int JOB_PROCESSING = 1;
    private static final int JOB_SUCCEEDED = 2;
    private static final int JOB_FAILED = 3;

    private static final String[] HEADERS = {
            "题型", "学科ID", "阶段ID", "题干",
            "选项A", "选项B", "选项C", "选项D", "选项E", "选项F",
            "正确答案", "解析", "评分点", "参考答案"};

    private static final int COL_TYPE = 0;
    private static final int COL_SUBJECT = 1;
    private static final int COL_STAGE = 2;
    private static final int COL_CONTENT = 3;
    private static final int COL_OPTION_START = 4;
    private static final int COL_ANSWER = 10;
    private static final int COL_ANALYSIS = 11;
    private static final int COL_POINTS = 12;
    private static final int COL_REFERENCE = 13;

    private static final int TYPE_TRUE_FALSE = 1;
    private static final int TYPE_SINGLE = 2;
    private static final int TYPE_MULTIPLE = 3;
    private static final int TYPE_SHORT_ANSWER = 4;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final AdminAuditService auditService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "question-import");
        thread.setDaemon(true);
        return thread;
    });

    public QuestionImportService(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                                 AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.auditService = auditService;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ 模板

    /** 模板：数据页（表头 + 4 种题型示例）+ 填写说明页 */
    public byte[] template() {
        List<String[]> data = new ArrayList<>();
        data.add(HEADERS);
        // 单选
        data.add(new String[]{"SINGLE_CHOICE", "1", "1", "以下哪个是 Spring 的核心容器接口？（示例行，可删除）",
                "BeanFactory", "ServletContext", "ApplicationContext 之外的容器", "DataSource",
                "", "", "A", "BeanFactory 是最基础的 IoC 容器接口。", "", ""});
        // 多选
        data.add(new String[]{"MULTIPLE_CHOICE", "1", "1", "下列属于 JVM 运行时数据区的有？（示例行，可删除）",
                "堆", "虚拟机栈", "寄存器文件", "方法区",
                "", "", "A,B,D", "堆、虚拟机栈、方法区都是 JVM 运行时数据区。", "", ""});
        // 判断
        data.add(new String[]{"TRUE_FALSE", "1", "1", "HashMap 是线程安全的。（示例行，可删除）",
                "", "", "", "", "", "", "错", "HashMap 非线程安全，并发场景用 ConcurrentHashMap。", "", ""});
        // 简答
        data.add(new String[]{"SHORT_ANSWER", "1", "1", "请说明索引失效的常见场景。（示例行，可删除）",
                "", "", "", "", "", "", "",
                "考察对索引原理与 SQL 写法的理解。",
                "提到隐式类型转换；提到前导模糊查询；提到函数包裹字段",
                "隐式类型转换、前导模糊查询、字段上使用函数、联合索引不满足最左前缀。"});

        List<String[]> notes = new ArrayList<>();
        notes.add(new String[]{"列名", "是否必填", "说明"});
        notes.add(new String[]{"题型", "必填", "TRUE_FALSE / SINGLE_CHOICE / MULTIPLE_CHOICE / SHORT_ANSWER"});
        notes.add(new String[]{"学科ID", "必填", "后台学科列表里的 ID（数字）"});
        notes.add(new String[]{"阶段ID", "必填", "该学科下的阶段 ID（数字），阶段必须属于所选学科"});
        notes.add(new String[]{"题干", "必填", "最长 2000 字；同阶段题干不能重复（忽略空格与大小写）"});
        notes.add(new String[]{"选项A-F", "条件必填", "选择/判断题必填；判断题固定填 A=正确、B=错误，仅需在两个列里写文案或留空由系统补"});
        notes.add(new String[]{"正确答案", "条件必填", "单选填字母如 A；多选填 A,B；判断题填 对/错；简答题留空"});
        notes.add(new String[]{"解析", "选填", "最长 2000 字"});
        notes.add(new String[]{"评分点", "简答必填", "用分号分隔，至少 1 条，每条最长 500 字"});
        notes.add(new String[]{"参考答案", "选填", "简答题参考答案，最长 2000 字"});
        notes.add(new String[]{"上限", "—", "单文件 ≤ 5MB、数据行 ≤ 1000 行；任一行校验失败则整批不写入"});

        return SimpleXlsx.write(List.of(
                new SimpleXlsx.Sheet("题目导入", data),
                new SimpleXlsx.Sheet("填写说明", notes)));
    }

    // ------------------------------------------------------------------ 启动导入

    public ImportStartResponse start(MultipartFile file, long adminId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.IMPORT_FILE_EMPTY);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(ErrorCode.IMPORT_FILE_TOO_LARGE);
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException(ErrorCode.IMPORT_FILE_TYPE_INVALID);
        }

        List<RawRow> rows;
        try {
            rows = extractRows(SimpleXlsx.readFirstSheet(file.getBytes()));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.IMPORT_FILE_EMPTY, "文件读取失败，请重新上传");
        } catch (RuntimeException ex) {
            log.warn("xlsx 解析失败: file={}", fileName, ex);
            throw new BizException(ErrorCode.IMPORT_FILE_TYPE_INVALID, "无法解析该 xlsx，请使用导入模板");
        }
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.IMPORT_FILE_EMPTY);
        }
        if (rows.size() > MAX_ROWS) {
            throw new BizException(ErrorCode.IMPORT_ROW_LIMIT_EXCEEDED);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO import_job (admin_user_id, file_name, status, total_rows, success_rows, "
                            + "failed_rows, error_message, started_at) VALUES (?, ?, ?, ?, 0, 0, '', ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, adminId);
            statement.setString(2, fileName);
            statement.setInt(3, JOB_PROCESSING);
            statement.setInt(4, rows.size());
            statement.setObject(5, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long jobId = key == null ? 0 : key.longValue();

        auditService.record("question", "import", "import_job", jobId,
                "发起题库导入，文件=" + fileName + "，行数=" + rows.size());
        executor.execute(() -> process(jobId, rows));
        return new ImportStartResponse(jobId, "PROCESSING", TimeFormats.rfc3339(now));
    }

    // ------------------------------------------------------------------ 任务查询

    public ImportTaskResponse status(long taskId, long adminId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, admin_user_id, status, total_rows, success_rows, failed_rows, error_message, "
                        + "created_at, finished_at FROM import_job WHERE id = ?", taskId);
        if (rows.isEmpty() || ((Number) rows.get(0).get("admin_user_id")).longValue() != adminId) {
            throw new BizException(ErrorCode.IMPORT_TASK_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);
        int status = intValue(row.get("status"));
        if (status == JOB_PROCESSING) {
            throw new BizException(ErrorCode.IMPORT_TASK_PROCESSING);
        }
        List<ImportTaskResponse.RowError> errors = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT row_no, column_name, error_message FROM import_job_error WHERE job_id = ? "
                        + "ORDER BY row_no ASC, id ASC LIMIT 200",
                rs -> {
                    errors.add(new ImportTaskResponse.RowError(rs.getInt("row_no"),
                            rs.getString("column_name"), rs.getString("error_message")));
                },
                taskId);
        String errorMessage = (String) row.get("error_message");
        if (errors.isEmpty() && errorMessage != null && !errorMessage.isBlank()) {
            errors.add(new ImportTaskResponse.RowError(0, "", errorMessage));
        }
        return new ImportTaskResponse(
                taskId,
                status == JOB_SUCCEEDED ? "SUCCEEDED" : "FAILED",
                intValue(row.get("total_rows")),
                intValue(row.get("success_rows")),
                intValue(row.get("failed_rows")),
                errors,
                TimeFormats.rfc3339(row.get("created_at")),
                TimeFormats.rfc3339(row.get("finished_at")));
    }

    // ------------------------------------------------------------------ 处理

    private void process(long jobId, List<RawRow> rows) {
        List<ImportTaskResponse.RowError> errors = new ArrayList<>();
        try {
            Integer inserted = transactionTemplate.execute(status -> {
                int count = 0;
                Set<String> seen = new HashSet<>();
                for (RawRow raw : rows) {
                    try {
                        insertRow(parse(raw, seen));
                        count++;
                    } catch (BizException ex) {
                        String column = ex.getData() instanceof String name ? name : "行";
                        errors.add(new ImportTaskResponse.RowError(raw.rowNo(), column, ex.getMessage()));
                    } catch (RuntimeException ex) {
                        log.warn("导入行异常 job={} row={}", jobId, raw.rowNo(), ex);
                        errors.add(new ImportTaskResponse.RowError(raw.rowNo(), "行", "写入失败，请检查数据后重试"));
                    }
                }
                if (!errors.isEmpty()) {
                    status.setRollbackOnly();
                    return 0;
                }
                return count;
            });
            if (errors.isEmpty()) {
                finish(jobId, JOB_SUCCEEDED, inserted == null ? rows.size() : inserted, 0, "", errors);
                return;
            }
        } catch (RuntimeException ex) {
            log.error("导入任务异常 job={}", jobId, ex);
            errors.clear();
            errors.add(new ImportTaskResponse.RowError(0, "", "导入过程出错，本批未写入任何题目"));
        }
        finish(jobId, JOB_FAILED, 0, errors.size(), summary(errors), errors);
    }

    /**
     * 结果写库：与导入事务分开，保证回滚后仍能记录失败原因。
     * 状态与错误明细必须在同一事务里提交，否则前端轮询可能读到 FAILED 但明细还没写完。
     */
    private void finish(long jobId, int status, int successRows, int failedRows,
                        String errorMessage, List<ImportTaskResponse.RowError> errors) {
        LocalDateTime finishedAt = LocalDateTime.now(ZoneOffset.UTC);
        transactionTemplate.executeWithoutResult(tx -> {
            jdbcTemplate.update(
                    "UPDATE import_job SET status = ?, success_rows = ?, failed_rows = ?, error_message = ?, "
                            + "finished_at = ? WHERE id = ?",
                    status, successRows, failedRows, truncate(errorMessage, 1000), finishedAt, jobId);
            for (ImportTaskResponse.RowError error : errors) {
                jdbcTemplate.update(
                        "INSERT INTO import_job_error (job_id, row_no, column_name, error_message, raw_row) "
                                + "VALUES (?, ?, ?, ?, NULL)",
                        jobId, error.rowNo(), truncate(error.column(), 50), truncate(error.message(), 500));
            }
        });
        if (status == JOB_SUCCEEDED) {
            log.info("题库导入成功 job={} rows={}", jobId, successRows);
        } else {
            log.warn("题库导入失败 job={} failedRows={}", jobId, failedRows);
        }
    }

    private static String summary(List<ImportTaskResponse.RowError> errors) {
        if (errors.isEmpty()) {
            return "导入失败";
        }
        ImportTaskResponse.RowError first = errors.get(0);
        return "第 " + first.rowNo() + " 行：" + first.message();
    }

    // ------------------------------------------------------------------ 行解析

    private ParsedQuestion parse(RawRow raw, Set<String> seen) {
        String type = cell(raw, COL_TYPE).toUpperCase(Locale.ROOT);
        int dbType;
        try {
            dbType = AdminQuestionService.typeToDb(type);
        } catch (BizException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "题型只能是 TRUE_FALSE/SINGLE_CHOICE/"
                    + "MULTIPLE_CHOICE/SHORT_ANSWER", "题型");
        }

        long subjectId = parseId(raw, COL_SUBJECT, "学科ID");
        long stageId = parseId(raw, COL_STAGE, "阶段ID");
        requireStageInSubject(stageId, subjectId);

        String content = cell(raw, COL_CONTENT);
        if (content.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "题干不能为空", "题干");
        }
        if (content.length() > 2000) {
            throw new BizException(ErrorCode.PARAM_INVALID, "题干不能超过 2000 字符", "题干");
        }
        String hash = AdminQuestionService.sha256(AdminQuestionService.normalize(content));
        if (!seen.add(stageId + "#" + hash)) {
            throw new BizException(ErrorCode.QUESTION_CONTENT_DUPLICATED, "文件内该阶段题干重复", "题干");
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question WHERE stage_id = ? AND content_hash = ?", Integer.class,
                stageId, hash);
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.QUESTION_CONTENT_DUPLICATED, "同阶段已存在相同题干的题目", "题干");
        }

        String analysis = cell(raw, COL_ANALYSIS);
        if (analysis.length() > 2000) {
            throw new BizException(ErrorCode.PARAM_INVALID, "解析不能超过 2000 字符", "解析");
        }
        String reference = cell(raw, COL_REFERENCE);
        if (reference.length() > 2000) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参考答案不能超过 2000 字符", "参考答案");
        }

        List<String> optionTexts = new ArrayList<>();
        for (int i = 0; i < MAX_OPTION_COLUMNS; i++) {
            optionTexts.add(cell(raw, COL_OPTION_START + i));
        }
        List<String> correctKeys = parseAnswer(cell(raw, COL_ANSWER), dbType);
        List<ParsedOption> options = dbType == TYPE_SHORT_ANSWER
                ? List.of() : buildOptions(dbType, optionTexts, correctKeys);
        List<String> points = parsePoints(cell(raw, COL_POINTS));

        if (dbType == TYPE_SHORT_ANSWER) {
            if (points.isEmpty()) {
                throw new BizException(ErrorCode.QUESTION_SCORING_POINT_REQUIRED, "简答题至少需要一条评分点", "评分点");
            }
            boolean hasOptionText = optionTexts.stream().anyMatch(text -> !text.isBlank());
            if (hasOptionText) {
                throw new BizException(ErrorCode.QUESTION_OPTION_INVALID, "简答题不要填写选项", "选项A");
            }
        } else if (!points.isEmpty()) {
            throw new BizException(ErrorCode.QUESTION_OPTION_INVALID, "评分点只用于简答题", "评分点");
        }

        return new ParsedQuestion(stageId, dbType, content, hash, analysis,
                dbType == TYPE_SHORT_ANSWER ? trimToNull(reference) : null, options, points);
    }

    private static List<String> parseAnswer(String raw, int dbType) {
        if (dbType == TYPE_SHORT_ANSWER) {
            return List.of();
        }
        String value = raw.trim();
        if (dbType == TYPE_TRUE_FALSE) {
            String normalized = value.toUpperCase(Locale.ROOT);
            if (normalized.startsWith("对") || "T".equals(normalized) || "TRUE".equals(normalized)
                    || "正确".equals(normalized) || "A".equals(normalized)) {
                return List.of("A");
            }
            if (normalized.startsWith("错") || "F".equals(normalized) || "FALSE".equals(normalized)
                    || "错误".equals(normalized) || "B".equals(normalized)) {
                return List.of("B");
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "判断题答案只能填「对/正确」或「错/错误」", "正确答案");
        }
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "选择题必须填写正确答案", "正确答案");
        }
        List<String> keys = new ArrayList<>();
        for (String part : value.split("[,，;；/|\\s]+")) {
            String key = part.trim().toUpperCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            if (key.length() != 1 || key.charAt(0) < 'A' || key.charAt(0) > 'Z') {
                throw new BizException(ErrorCode.PARAM_INVALID, "正确答案请填选项字母，如 A 或 A,B", "正确答案");
            }
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        if (dbType == TYPE_SINGLE && keys.size() != 1) {
            throw new BizException(ErrorCode.QUESTION_OPTION_INVALID, "单选题只能有 1 个正确答案", "正确答案");
        }
        if (dbType == TYPE_MULTIPLE && keys.size() < 2) {
            throw new BizException(ErrorCode.QUESTION_OPTION_INVALID, "多选题至少要有 2 个正确答案", "正确答案");
        }
        return keys;
    }

    private static List<ParsedOption> buildOptions(int dbType, List<String> texts, List<String> correctKeys) {
        List<ParsedOption> options = new ArrayList<>();
        if (dbType == TYPE_TRUE_FALSE) {
            options.add(new ParsedOption("A", "正确", correctKeys.contains("A"), 0));
            options.add(new ParsedOption("B", "错误", correctKeys.contains("B"), 1));
            return options;
        }
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text.isBlank()) {
                continue;
            }
            if (text.length() > 1000) {
                throw new BizException(ErrorCode.PARAM_INVALID, "选项内容不能超过 1000 字符",
                        "选项" + (char) ('A' + i));
            }
            String key = String.valueOf((char) ('A' + i));
            options.add(new ParsedOption(key, text, correctKeys.contains(key), options.size()));
        }
        if (options.size() < 2) {
            throw new BizException(ErrorCode.QUESTION_OPTION_INVALID, "选择题至少需要 2 个选项", "选项A");
        }
        for (String key : correctKeys) {
            boolean matched = options.stream().anyMatch(option -> option.key().equals(key));
            if (!matched) {
                throw new BizException(ErrorCode.QUESTION_OPTION_INVALID,
                        "正确答案 " + key + " 没有对应选项", "正确答案");
            }
        }
        long correctCount = options.stream().filter(ParsedOption::isCorrect).count();
        if (correctCount != correctKeys.size() || correctCount == 0) {
            throw new BizException(ErrorCode.QUESTION_OPTION_INVALID, "正确答案标记与选项不匹配", "正确答案");
        }
        return options;
    }

    private static List<String> parsePoints(String raw) {
        List<String> points = new ArrayList<>();
        if (raw.isBlank()) {
            return points;
        }
        for (String part : raw.split("[;；\\n]+")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > 500) {
                throw new BizException(ErrorCode.PARAM_INVALID, "单条评分点不能超过 500 字符", "评分点");
            }
            points.add(text);
        }
        return points;
    }

    private void requireStageInSubject(long stageId, long subjectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.subject_id FROM stage st JOIN learning_path p ON p.id = st.path_id WHERE st.id = ?",
                stageId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "阶段不存在", "阶段ID");
        }
        if (((Number) rows.get(0).get("subject_id")).longValue() != subjectId) {
            throw new BizException(ErrorCode.PARAM_INVALID, "阶段不属于所选学科", "阶段ID");
        }
    }

    // ------------------------------------------------------------------ 写库

    private void insertRow(ParsedQuestion question) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO question (stage_id, type, content, content_hash, analysis, reference_answer, "
                            + "status, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, 1, 0, 0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, question.stageId());
            statement.setInt(2, question.type());
            statement.setString(3, question.content());
            statement.setString(4, question.hash());
            statement.setString(5, question.analysis());
            statement.setString(6, question.referenceAnswer());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long questionId = key == null ? 0 : key.longValue();

        for (ParsedOption option : question.options()) {
            jdbcTemplate.update(
                    "INSERT INTO question_option (question_id, option_key, content, is_correct, sort_no) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    questionId, option.key(), option.content(), option.isCorrect() ? 1 : 0, option.sortNo());
        }
        for (int i = 0; i < question.points().size(); i++) {
            jdbcTemplate.update(
                    "INSERT INTO question_scoring_point (question_id, point_text, sort_no) VALUES (?, ?, ?)",
                    questionId, question.points().get(i), i);
        }
    }

    // ------------------------------------------------------------------ 工具

    private static List<RawRow> extractRows(List<List<String>> sheet) {
        List<RawRow> rows = new ArrayList<>();
        for (int i = 1; i < sheet.size(); i++) {
            List<String> cells = sheet.get(i);
            if (cells.stream().allMatch(value -> value == null || value.isBlank())) {
                continue;
            }
            rows.add(new RawRow(i + 1, cells));
        }
        return rows;
    }

    private static String cell(RawRow row, int index) {
        List<String> cells = row.cells();
        if (index >= cells.size()) {
            return "";
        }
        String value = cells.get(index);
        return value == null ? "" : value.trim();
    }

    private static long parseId(RawRow row, int index, String column) {
        String value = cell(row, index);
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, column + "不能为空", column);
        }
        try {
            // Excel 数字列可能带小数点
            return (long) Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, column + "必须是数字", column);
        }
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record RawRow(int rowNo, List<String> cells) {
    }

    private record ParsedOption(String key, String content, boolean isCorrect, int sortNo) {
    }

    private record ParsedQuestion(long stageId, int type, String content, String hash, String analysis,
                                  String referenceAnswer, List<ParsedOption> options, List<String> points) {
    }
}
