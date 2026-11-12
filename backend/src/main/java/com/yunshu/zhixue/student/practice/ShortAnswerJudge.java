package com.yunshu.zhixue.student.practice;

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
import java.util.List;

/**
 * 简答题判分（PRD 5.5 / 02 §5.2）：按评分点逐点判断命中率，得分率 ≥ 60% 判对。
 *
 * <p>超时 8 秒；调用失败、返回不可解析或降级一律返回 {@link Outcome#degraded()}，
 * 由调用方写入"待自评"状态让学员自评 —— 绝不能把失败当成 0 分。</p>
 *
 * <p>本组件不做事务控制；调用方必须保证在事务外调用（rules 禁止在事务内调大模型）。</p>
 */
@Component
public class ShortAnswerJudge {

    private static final Logger log = LoggerFactory.getLogger(ShortAnswerJudge.class);

    private static final String MODEL = "qwen-plus";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final double PASS_SCORE = 60d;

    private static final String SYSTEM_PROMPT = """
            你是严谨的编程学习阅卷老师。请按"评分点"逐条判断学员回答是否覆盖该要点，\
            并给出 0-100 的得分（命中评分点数 / 评分点总数 × 100，四舍五入取整）。\
            只输出 JSON，不要输出解释、Markdown 或代码块标记。\
            JSON 结构：{"score":整数,"isCorrect":布尔,"analysis":"80 字内点评","hitPoints":[{"point":"评分点原文","hit":布尔}]}""";

    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;

    public ShortAnswerJudge(LlmProvider llmProvider, ObjectMapper objectMapper) {
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
    }

    public Outcome judge(String content, List<String> scoringPoints, String referenceAnswer, String answerText) {
        String userPrompt = buildPrompt(content, scoringPoints, referenceAnswer, answerText);
        LlmResponse response;
        try {
            response = llmProvider.complete(new LlmRequest(MODEL, SYSTEM_PROMPT, userPrompt, TIMEOUT, null));
        } catch (RuntimeException ex) {
            log.warn("简答判分调用失败，降级为自评", ex);
            return Outcome.degradedOf(scoringPoints);
        }
        if (response == null || response.degraded() || response.content() == null || response.content().isBlank()) {
            log.info("简答判分返回降级结果，转学员自评");
            return Outcome.degradedOf(scoringPoints);
        }
        Outcome parsed = parse(response.content(), scoringPoints);
        if (parsed == null) {
            log.warn("简答判分返回内容不可解析，降级为自评: {}", abbreviate(response.content()));
            return Outcome.degradedOf(scoringPoints);
        }
        return parsed;
    }

    private String buildPrompt(String content, List<String> scoringPoints, String referenceAnswer, String answerText) {
        StringBuilder builder = new StringBuilder(512);
        builder.append("题目：").append(content).append('\n');
        if (!scoringPoints.isEmpty()) {
            builder.append("评分点：\n");
            for (int i = 0; i < scoringPoints.size(); i++) {
                builder.append(i + 1).append(". ").append(scoringPoints.get(i)).append('\n');
            }
        }
        if (referenceAnswer != null && !referenceAnswer.isBlank()) {
            builder.append("参考答案：").append(referenceAnswer).append('\n');
        }
        builder.append("学员回答：").append(answerText);
        return builder.toString();
    }

    /** 解析模型输出；结构不符返回 null（调用方按降级处理） */
    private Outcome parse(String raw, List<String> scoringPoints) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw.substring(start, end + 1));
            JsonNode scoreNode = root.get("score");
            if (scoreNode == null || !scoreNode.isNumber()) {
                return null;
            }
            double score = Math.max(0d, Math.min(100d, scoreNode.asDouble()));
            boolean correct = root.has("isCorrect") && root.get("isCorrect").isBoolean()
                    ? root.get("isCorrect").asBoolean()
                    : score >= PASS_SCORE;
            String analysis = root.hasNonNull("analysis") ? root.get("analysis").asText() : "";

            List<Outcome.HitPoint> hits = new ArrayList<>();
            JsonNode pointsNode = root.get("hitPoints");
            if (pointsNode != null && pointsNode.isArray()) {
                for (JsonNode node : pointsNode) {
                    String point = node.hasNonNull("point") ? node.get("point").asText() : "";
                    boolean hit = node.has("hit") && node.get("hit").asBoolean();
                    if (!point.isBlank()) {
                        hits.add(new Outcome.HitPoint(point, hit));
                    }
                }
            } else {
                for (String point : scoringPoints) {
                    hits.add(new Outcome.HitPoint(point, false));
                }
            }
            return new Outcome(false, score, correct, analysis, hits);
        } catch (Exception ex) {
            log.debug("解析简答判分 JSON 失败", ex);
            return null;
        }
    }

    private static String abbreviate(String value) {
        return value.length() <= 120 ? value : value.substring(0, 120) + "…";
    }

    /** 判分结果；`degraded=true` 时 score/correct 为 null，业务层写"待自评" */
    public record Outcome(
            boolean degraded,
            Double score,
            Boolean correct,
            String feedback,
            List<HitPoint> hitPoints
    ) {

        static Outcome degradedOf(List<String> scoringPoints) {
            List<HitPoint> points = new ArrayList<>(scoringPoints.size());
            for (String point : scoringPoints) {
                points.add(new HitPoint(point, false));
            }
            return new Outcome(true, null, null, "", points);
        }

        public record HitPoint(String point, boolean hit) {
        }
    }
}
