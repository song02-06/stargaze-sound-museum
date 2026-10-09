package com.starmuseum.sign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 真模型路径（OpenAI 兼容接口）。
 *
 * 关键：**提示词是硬约束，代码里的 SignGuard 是硬保险。**
 * 提示词写得再狠也可能被绕过，所以模型输出一定要过 SignGuard；
 * 过不了就直接退回 MockSignCompressor —— 宁可展签朴素一点，不能造假。
 */
public class RemoteSignCompressor implements SignCompressor {

    private static final Logger log = LoggerFactory.getLogger(RemoteSignCompressor.class);

    private static final String SYSTEM_PROMPT = """
            你是博物馆的展签编辑。你只能做减法，绝不能做加法。

            规则（违反任何一条，输出作废）：
            1. 只能从用户原文里删字、并句、去掉「嗯／那个／然后就是」这类口癖。
            2. 禁止新增任何词。禁止扩写。禁止添加用户没说过的地点、时间、人物、情绪。
            3. 禁止改写句式。输出必须是原文的字符子序列。
            4. 目标长度 %d 字以内，尽量保住带时间、地点、数字的那一两句。
            5. 只输出展签正文本身，不要引号，不要解释，不要标题。
            """;

    private final RestClient client;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public RemoteSignCompressor(String baseUrl, String apiKey, String model, int timeoutMs) {
        this.model = model;
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofMillis(3000));
                    setReadTimeout(Duration.ofMillis(timeoutMs));
                }})
                .build();
    }

    @Override
    public String provider() {
        return "remote";
    }

    @Override
    public String compress(String fullText, String title, int maxChars) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT.formatted(maxChars)),
                            Map.of("role", "user", "content", "标题：" + title + "\n全文：\n" + fullText)
                    )
            );

            String raw = client.post()
                    .uri("/v1/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return stripFence(content).strip();
        } catch (Exception e) {
            log.warn("展签模型调用失败，交回调用方走兜底：{}", e.getMessage());
            return "";
        }
    }

    static String stripFence(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("^\\s*```(?:json)?\\s*", "")
                .replaceAll("\\s*```\\s*$", "")
                .strip();
    }
}
