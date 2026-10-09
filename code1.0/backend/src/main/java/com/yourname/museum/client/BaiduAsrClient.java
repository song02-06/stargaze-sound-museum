package com.yourname.museum.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yourname.museum.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * 百度短语音识别（REST 版，不引入 SDK 依赖）。
 *
 * <p>启用方式：application.yml 里把 museum.asr.provider 改成 baidu，
 * 并配好 BAIDU_APP_ID / BAIDU_API_KEY / BAIDU_SECRET_KEY 三个环境变量。
 *
 * <p>注意四个硬性约束（详见实施手册 1.4）：
 * <ol>
 *   <li>只接受 16k 采样率的 WAV/PCM —— 浏览器原生录的 webm/opus 必须先转码；</li>
 *   <li>单次时长上限约 60 秒；</li>
 *   <li>err_no != 0 即失败，必须显式判断；</li>
 *   <li>接口只返回文本，没有情绪/语种/音频事件标签。</li>
 * </ol>
 *
 * <p><b>提示</b>：本类按官方 REST 文档编写，但作者未用真实 Key 联网验证过。
 * 首次接入的真实任务请放到「阶段 1 任务 A」里，先用官方示例确认额度生效，再跑这里。
 */
@Service
@ConditionalOnProperty(name = "museum.asr.provider", havingValue = "baidu")
public class BaiduAsrClient implements AsrClient {

    private static final Logger log = LoggerFactory.getLogger(BaiduAsrClient.class);
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String ASR_URL = "https://vop.baidu.com/server_api";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String appId;
    private final String apiKey;
    private final String secretKey;

    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    public BaiduAsrClient(@Value("${museum.asr.baidu.app-id}") String appId,
                          @Value("${museum.asr.baidu.api-key}") String apiKey,
                          @Value("${museum.asr.baidu.secret-key}") String secretKey) {
        this.appId = appId;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("museum.asr.provider=baidu 但没有配置 BAIDU_API_KEY，调用会失败");
        }
    }

    @Override
    public AsrResult transcribe(Path audioFile) {
        long start = System.currentTimeMillis();
        try {
            byte[] bytes = Files.readAllBytes(audioFile);
            ObjectNode body = mapper.createObjectNode();
            body.put("format", "wav");
            body.put("rate", 16000);
            body.put("channel", 1);
            body.put("cuid", "starrail-museum");
            body.put("token", accessToken());
            body.put("len", bytes.length);
            body.put("speech", Base64.getEncoder().encodeToString(bytes));

            String raw = restClient.post()
                    .uri(ASR_URL)
                    .header("Content-Type", "application/json")
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(raw);
            if (node.path("err_no").asInt(-1) != 0) {
                return AsrResult.fail("ASR 失败: " + node.path("err_msg").asText(), cost(start));
            }
            String text = node.path("result").path(0).asText("");
            return AsrResult.ok(text, cost(start));
        } catch (Exception e) {
            log.error("baidu asr failed", e);
            return AsrResult.fail("ASR 调用异常: " + e.getMessage(), cost(start));
        }
    }

    private String accessToken() throws Exception {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
                return cachedToken;
            }
            String raw = restClient.post()
                    .uri(TOKEN_URL + "?grant_type=client_credentials&client_id={id}&client_secret={secret}",
                            Map.of("id", apiKey, "secret", secretKey))
                    .retrieve()
                    .body(String.class);
            JsonNode node = mapper.readTree(raw);
            if (!node.hasNonNull("access_token")) {
                throw new BizException("获取百度 access_token 失败：" + raw);
            }
            cachedToken = node.get("access_token").asText();
            // 官方有效期 30 天，这里保守按 25 天缓存
            tokenExpireAt = System.currentTimeMillis() + 25L * 24 * 3600 * 1000;
            return cachedToken;
        }
    }

    private int cost(long start) {
        return (int) (System.currentTimeMillis() - start);
    }

    @Override
    public String providerName() {
        return "baidu";
    }

    public String appIdOf() {
        return appId;
    }
}
