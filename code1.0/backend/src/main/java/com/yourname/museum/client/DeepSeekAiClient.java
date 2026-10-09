package com.yourname.museum.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * DeepSeek API（OpenAI 兼容）实现。
 *
 * <p>三个容易踩的坑，都已经在这里处理：
 * <ol>
 *   <li>模型爱把 JSON 包在 ```json 代码块里 —— {@link #stripCodeFence} 负责剥掉；</li>
 *   <li>没设超时会导致线程堆积 —— 统一设 connect/read timeout；</li>
 *   <li>任何失败都不许把上传流程带崩 —— 全部 catch 成 AiResult.fallback。</li>
 * </ol>
 */
@Service
@ConditionalOnProperty(name = "museum.ai.provider", havingValue = "deepseek")
public class DeepSeekAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiClient.class);

    private static final String SYSTEM_PROMPT = """
            你是「星轨·声音博物馆」的文本整理员。用户会给你一段语音转写的文字。
            请严格按下面要求输出一个 JSON 对象，不要输出任何其它内容：
            {
              "title": "不超过 12 字的中文标题",
              "story": "80-150 字的第二人称或第三人称小故事，有画面感，保持温柔克制",
              "tags": "3 个中文标签，用英文逗号分隔",
              "emotion": "一个情绪词，如 怀念/温柔/遗憾/释然",
              "safe": true 或 false,
              "level": "LOW 或 MEDIUM 或 HIGH",
              "reason": "一句话说明判定理由"
            }
            硬性约束：
            1. 绝对不能虚构原文里没有的事实、人名、地名、时间；
            2. 原文有脏话、广告、违法内容时 safe=false，level 按严重程度给；
            3. 只输出 JSON，不要 Markdown 代码块，不要解释。
            """;

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;

    public DeepSeekAiClient(@Value("${museum.ai.deepseek.base-url}") String baseUrl,
                            @Value("${museum.ai.deepseek.api-key}") String apiKey,
                            @Value("${museum.ai.deepseek.model}") String model,
                            @Value("${museum.ai.deepseek.connect-timeout-ms:3000}") int connectTimeoutMs,
                            @Value("${museum.ai.deepseek.read-timeout-ms:15000}") int readTimeoutMs) {
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public AiResult polish(String rawText, String originalNote) {
        long start = System.currentTimeMillis();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.7);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
            messages.addObject().put("role", "user").put("content", buildUserContent(rawText, originalNote));

            String raw = restClient.post()
                    .uri("/chat/completions")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode result = mapper.readTree(stripCodeFence(content));

            return new AiResult(
                    result.path("title").asText("未命名的声音"),
                    result.path("story").asText(rawText),
                    result.path("tags").asText("未分类"),
                    result.path("emotion").asText("平静"),
                    result.path("safe").asBoolean(true),
                    result.path("level").asText("LOW"),
                    result.path("reason").asText(""),
                    false,
                    (int) (System.currentTimeMillis() - start),
                    raw
            );
        } catch (Exception e) {
            log.error("deepseek call failed, fallback to raw text", e);
            return AiResult.fallback(rawText, e.getMessage(), (int) (System.currentTimeMillis() - start));
        }
    }

    private String buildUserContent(String rawText, String originalNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("语音转写原文：\n").append(rawText == null ? "" : rawText);
        if (originalNote != null && !originalNote.isBlank()) {
            sb.append("\n\n用户自己补充的一句话：\n").append(originalNote);
        }
        return sb.toString();
    }

    /** 剥掉 ```json ... ``` 包裹，避免 JSON 解析失败。 */
    static String stripCodeFence(String content) {
        if (content == null) {
            return "{}";
        }
        String s = content.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) {
                s = s.substring(0, lastFence);
            }
        }
        return s.trim();
    }

    @Override
    public String providerName() {
        return "deepseek";
    }

    @Override
    public String modelName() {
        return model;
    }
}
