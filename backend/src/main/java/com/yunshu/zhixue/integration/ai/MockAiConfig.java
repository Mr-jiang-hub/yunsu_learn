package com.yunshu.zhixue.integration.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开发环境 AI mock：不访问任何厂商，用于业务联调前的工程自检。
 * 生产 Profile 会因 provider != mock 且缺少真实适配器而启动失败。
 *
 * <p>面试相关能力要求模型返回结构化 JSON，故此处按 {@code metadata.task} 返回固定 JSON，
 * 让"出题 → 追问 → 报告"全链路在无厂商密钥时也能跑通；未登记的 task 保持原样回显，
 * 便于确认提示词内容。需要联调降级分支时，用
 * {@code integration.ai.mock.degraded-tasks=interview.opening,interview.followup} 指定。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "integration.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiConfig {

    private static final Logger log = LoggerFactory.getLogger(MockAiConfig.class);

    private static final String OPENING_JSON =
            "{\"question\":\"请先做个自我介绍，重点说说你最近在学的技术方向，以及学这块时卡住过的地方。\"}";

    private static final String FOLLOWUP_JSON =
            "{\"question\":\"你刚才提到的做法，如果把访问量放大十倍，你会优先调整哪一部分？为什么？\"}";

    /** 报告里的维度名由提示词里的编号列表回填，保证 dev 数据与真实维度对得上 */
    private static final Pattern NUMBERED_LINE = Pattern.compile("(?m)^\\s*\\d+\\.\\s*(.+?)\\s*$");

    @Value("${integration.ai.mock.degraded-tasks:}")
    private String degradedTasks;

    @Bean
    public LlmProvider mockLlmProvider() {
        return request -> {
            String task = taskOf(request);
            log.debug("mock LLM 调用: model={}, task={}, promptLength={}", request.model(), task,
                    request.userPrompt() == null ? 0 : request.userPrompt().length());
            if (isDegraded(task)) {
                log.info("mock LLM 按要求返回降级结果: task={}", task);
                return new LlmResponse("", request.model(), 0, 0, true);
            }
            String content = switch (task) {
                case "interview.opening" -> OPENING_JSON;
                case "interview.followup" -> FOLLOWUP_JSON;
                case "interview.report" -> reportJson(request.userPrompt());
                default -> "[mock-llm] " + request.userPrompt();
            };
            return new LlmResponse(content, request.model(), 0, 0, false);
        };
    }

    @Bean
    public AsrProvider mockAsrProvider() {
        return (audio, contentType, audioDuration) -> {
            log.debug("mock ASR 调用: bytes={}, contentType={}", audio == null ? 0 : audio.length, contentType);
            return new AsrResponse("[mock-asr] 语音转写示例", false);
        };
    }

    @Bean
    public TtsProvider mockTtsProvider() {
        return text -> {
            log.debug("mock TTS 调用（P0 未启用）: length={}", text == null ? 0 : text.length());
            return new byte[0];
        };
    }

    private static String taskOf(LlmRequest request) {
        Object task = request.metadata().get("task");
        return task == null ? "" : String.valueOf(task);
    }

    private boolean isDegraded(String task) {
        if (task.isEmpty() || degradedTasks == null || degradedTasks.isBlank()) {
            return false;
        }
        for (String item : degradedTasks.split(",")) {
            if (item.trim().equals(task)) {
                return true;
            }
        }
        return false;
    }

    /** 从提示词的编号列表提取维度名（去重上限 6 条），按序递减给分 */
    private static String reportJson(String prompt) {
        Set<String> dimensions = new LinkedHashSet<>();
        if (prompt != null) {
            Matcher matcher = NUMBERED_LINE.matcher(prompt);
            while (matcher.find() && dimensions.size() < 6) {
                dimensions.add(matcher.group(1));
            }
        }
        List<String> names = new ArrayList<>(dimensions);
        StringBuilder builder = new StringBuilder(256);
        builder.append("{\"totalScore\":82.5,")
                .append("\"summary\":\"整体表达清晰，关键概念能讲明白，场景取舍和量化论证还可以再具体一些。\",")
                .append("\"suggestions\":[\"回答时先给结论再展开，控制单轮时长\",\"多用具体项目里的数字说明取舍依据\"],")
                .append("\"dimensions\":[");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            double score = Math.max(60d, 88d - i * 4d);
            builder.append("{\"name\":\"").append(escape(names.get(i))).append("\",\"score\":").append(score).append('}');
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
