package com.yunshu.zhixue.student.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.integration.ai.LlmProvider;
import com.yunshu.zhixue.integration.ai.LlmRequest;
import com.yunshu.zhixue.integration.ai.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试出题与评分（PRD 5.7 / rules §11.1）：开场题、追问、报告。
 *
 * <p>三条硬约束：</p>
 * <ol>
 *   <li>只输出 JSON，解析失败按"失败"处理并返回 null —— 绝不用 0 分表示失败；</li>
 *   <li>每次调用带超时并按 rules 重试 1 次，仍失败交给调用方降级；</li>
 *   <li>本组件不碰事务与数据库，调用方必须在事务外调用。</li>
 * </ol>
 */
@Component
public class InterviewAiClient {

    private static final Logger log = LoggerFactory.getLogger(InterviewAiClient.class);

    private static final String MODEL = "qwen-plus";
    private static final Duration QUESTION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REPORT_TIMEOUT = Duration.ofSeconds(15);

    private static final String QUESTION_SYSTEM = """
            你是一名资深技术面试官，正在与候选人做 4 轮模拟面试（第 1 轮开场，第 2-4 轮追问）。\
            只输出 JSON，不要输出解释、Markdown 或代码块标记。\
            JSON 结构：{"question":"一句话问题，不超过 80 字"}""";

    private static final String REPORT_SYSTEM = """
            你是严谨的技术面试评估官。请根据整场面试对话给出结构化评估。\
            只输出 JSON，不要输出解释、Markdown 或代码块标记。\
            JSON 结构：{"totalScore":0-100 的数字,"summary":"120 字内总评",\
            "suggestions":["改进建议1","改进建议2"],\
            "dimensions":[{"name":"给定维度名原文","score":0-100 的数字}]}。\
            dimensions 必须覆盖提示词中列出的每一个能力维度，name 必须与给定时一字不差。""";

    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;

    public InterviewAiClient(LlmProvider llmProvider, ObjectMapper objectMapper) {
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
    }

    /** 开场题；失败返回 null（调用方按 50002 作废并返还次数） */
    public String openingQuestion(Context context) {
        String userPrompt = buildContextPrompt(context)
                + "请给出第 1 轮开场问题：先让候选人自我介绍或说明近期技术方向，再引到本阶段考点。";
        return askQuestion("interview.opening", userPrompt);
    }

    /** 追问；失败返回 null（调用方按"未完成"结束会话） */
    public String followUp(Context context, List<Turn> turns, int nextRound) {
        StringBuilder builder = new StringBuilder(buildContextPrompt(context));
        builder.append("已有对话：\n").append(renderTurns(turns));
        builder.append("请针对候选人上一轮回答给出第 ").append(nextRound).append(" 轮追问：")
                .append("要顺着回答里的具体表述深挖，不要重复已问过的内容，也不要换话题。");
        return askQuestion("interview.followup", builder.toString());
    }

    /** 报告；失败返回 null（调用方不建报告，报告接口按 30051 处理） */
    public ReportDraft report(Context context, List<Turn> turns, boolean incomplete) {
        StringBuilder builder = new StringBuilder(buildContextPrompt(context));
        builder.append("面试对话：\n").append(renderTurns(turns));
        if (incomplete) {
            builder.append("注意：本次面试未完成，仅按已答轮次评分，不要臆测未作答内容。\n");
        }
        builder.append("请按能力维度逐项打分并给出总评与改进建议。");
        LlmResponse response = call("interview.report", REPORT_SYSTEM, builder.toString(), REPORT_TIMEOUT);
        if (response == null) {
            return null;
        }
        return parseReport(response.content());
    }

    // ------------------------------------------------------------------ 内部

    private String askQuestion(String task, String userPrompt) {
        LlmResponse response = call(task, QUESTION_SYSTEM, userPrompt, QUESTION_TIMEOUT);
        if (response == null) {
            return null;
        }
        JsonNode root = readJson(response.content());
        if (root == null) {
            log.warn("面试 {} 返回内容不可解析: {}", task, abbreviate(response.content()));
            return null;
        }
        JsonNode question = root.get("question");
        if (question == null || !question.isTextual() || question.asText().isBlank()) {
            log.warn("面试 {} 返回缺少 question 字段", task);
            return null;
        }
        return question.asText().trim();
    }

    /** 带 1 次重试的调用；全部失败返回 null。耗时与降级情况必须可观测（rules 11.1.5） */
    private LlmResponse call(String task, String systemPrompt, String userPrompt, Duration timeout) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            long start = System.currentTimeMillis();
            try {
                LlmResponse response = llmProvider.complete(new LlmRequest(
                        MODEL, systemPrompt, userPrompt, timeout, Map.of("task", task)));
                long cost = System.currentTimeMillis() - start;
                if (response != null && !response.degraded()
                        && response.content() != null && !response.content().isBlank()) {
                    log.debug("面试 AI 调用成功: task={}, attempt={}, cost={}ms, promptTokens={}, completionTokens={}",
                            task, attempt, cost, response.promptTokens(), response.completionTokens());
                    return response;
                }
                log.warn("面试 AI 返回降级结果: task={}, attempt={}, cost={}ms", task, attempt, cost);
            } catch (RuntimeException ex) {
                log.warn("面试 AI 调用异常: task={}, attempt={}, cost={}ms",
                        task, attempt, System.currentTimeMillis() - start, ex);
            }
        }
        return null;
    }

    private ReportDraft parseReport(String raw) {
        JsonNode root = readJson(raw);
        if (root == null || !root.hasNonNull("totalScore") || !root.get("totalScore").isNumber()) {
            log.warn("面试报告返回内容不可解析: {}", abbreviate(raw));
            return null;
        }
        double totalScore = clamp(root.get("totalScore").asDouble());
        String summary = root.hasNonNull("summary") ? root.get("summary").asText().trim() : "";

        List<String> suggestions = new ArrayList<>();
        JsonNode suggestionsNode = root.get("suggestions");
        if (suggestionsNode != null && suggestionsNode.isArray()) {
            for (JsonNode node : suggestionsNode) {
                if (node.isTextual() && !node.asText().isBlank()) {
                    suggestions.add(node.asText().trim());
                }
            }
        }

        Map<String, Double> dimensionScores = new LinkedHashMap<>();
        JsonNode dimensionsNode = root.get("dimensions");
        if (dimensionsNode != null && dimensionsNode.isArray()) {
            for (JsonNode node : dimensionsNode) {
                JsonNode name = node.get("name");
                JsonNode score = node.get("score");
                if (name != null && name.isTextual() && score != null && score.isNumber()) {
                    dimensionScores.put(name.asText().trim(), clamp(score.asDouble()));
                }
            }
        }
        return new ReportDraft(totalScore, summary, suggestions, dimensionScores);
    }

    private JsonNode readJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(raw.substring(start, end + 1));
        } catch (Exception ex) {
            log.debug("解析面试 AI JSON 失败", ex);
            return null;
        }
    }

    private static String buildContextPrompt(Context context) {
        StringBuilder builder = new StringBuilder(512);
        builder.append("学科：").append(context.subjectName()).append('\n');
        builder.append("阶段：").append(context.stageName()).append('\n');
        builder.append("难度：").append(context.difficultyName());
        if (context.difficultyFocus() != null && !context.difficultyFocus().isBlank()) {
            builder.append("（考察重点：").append(context.difficultyFocus()).append('）');
        }
        builder.append('\n');
        if (!context.dimensions().isEmpty()) {
            builder.append("能力维度：\n");
            for (int i = 0; i < context.dimensions().size(); i++) {
                builder.append(i + 1).append(". ").append(context.dimensions().get(i)).append('\n');
            }
        }
        if (!context.sampleQuestions().isEmpty()) {
            builder.append("本阶段题目示例（用于把握难度与范围，不要照搬）：\n");
            for (String sample : context.sampleQuestions()) {
                builder.append("- ").append(sample).append('\n');
            }
        }
        return builder.toString();
    }

    private static String renderTurns(List<Turn> turns) {
        StringBuilder builder = new StringBuilder(512);
        for (Turn turn : turns) {
            builder.append("第").append(turn.round()).append("轮 面试官：").append(turn.question()).append('\n');
            builder.append("第").append(turn.round()).append("轮 候选人：").append(turn.answer()).append('\n');
        }
        return builder.toString();
    }

    private static double clamp(double value) {
        return Math.round(Math.max(0d, Math.min(100d, value)) * 100d) / 100d;
    }

    private static String abbreviate(String value) {
        return value.length() <= 120 ? value : value.substring(0, 120) + "…";
    }

    /** 出题所需的上下文快照；阶段与难度均为会话创建时快照 */
    public record Context(
            String subjectName,
            String stageName,
            String difficultyName,
            String difficultyFocus,
            List<String> dimensions,
            List<String> sampleQuestions
    ) {
    }

    /** 一轮完整对话 */
    public record Turn(int round, String question, String answer) {
    }

    /** 报告草稿：`dimensionScores` 以维度名为键，落库前再映射回 dimension.id */
    public record ReportDraft(
            double totalScore,
            String summary,
            List<String> suggestions,
            Map<String, Double> dimensionScores
    ) {
    }
}
