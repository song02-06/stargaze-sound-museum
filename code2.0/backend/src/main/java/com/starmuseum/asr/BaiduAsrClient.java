package com.starmuseum.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 百度短语音识别（REST 版，不引 SDK）。
 *
 * 走 REST 而不是官方 java-sdk 是有意的：那个 SDK 最后一次更新是 2023 年，
 * 而 REST 接口稳定、只要一次 HTTP 调用。少一个三年没动的依赖。
 *
 * 四个硬性约束（来自官方文档）：
 *   1）只吃 16k 采样率、单声道、16 位的 WAV/PCM；
 *   2）单次时长上限 60 秒；
 *   3）err_no != 0 就是失败，必须显式判断 —— 不能当成功；
 *   4）只返回文本，没有情绪/语种/音频事件。
 *
 * access_token 有效期 30 天，这里缓存起来，避免每次转写都去换一次。
 */
public class BaiduAsrClient implements AsrClient {

    private static final Logger log = LoggerFactory.getLogger(BaiduAsrClient.class);
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String ASR_URL = "https://vop.baidu.com/server_api";

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String appId;
    private final String apiKey;
    private final String secretKey;
    private final int devPid;

    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    public BaiduAsrClient(String appId, String apiKey, String secretKey, int devPid) {
        this.appId = appId;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.devPid = devPid;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().requestFactory(factory).build();

        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.warn("museum.asr.provider=baidu，但 API Key 或 Secret Key 是空的，调用一定失败");
        }
    }

    @Override
    public String provider() {
        return "baidu";
    }

    @Override
    public AsrResult transcribe(Path wavFile) {
        long start = System.currentTimeMillis();
        try {
            byte[] bytes = Files.readAllBytes(wavFile);
            String speech = Base64.getEncoder().encodeToString(bytes);

            ObjectNode body = mapper.createObjectNode();
            body.put("format", "wav");
            body.put("rate", 16000);
            body.put("channel", 1);
            body.put("cuid", "starmuseum-" + (appId == null ? "unknown" : appId));
            body.put("token", accessToken());
            body.put("len", bytes.length);
            body.put("speech", speech);
            body.put("dev_pid", devPid);

            String raw = client.post()
                    .uri(ASR_URL)
                    .header("Content-Type", "application/json")
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(raw);
            int errNo = node.path("err_no").asInt(-1);

            if (errNo != 0) {
                String msg = node.path("err_msg").asText("未知错误");
                String hint = hintFor(errNo);
                log.warn("百度 ASR 返回 err_no={} msg={} {}", errNo, msg, hint);
                return AsrResult.fail("识别服务返回 " + errNo + "：" + msg + hint, cost(start));
            }

            List<String> words = new ArrayList<>();
            for (JsonNode w : node.path("result")) {
                words.add(w.asText());
            }
            if (words.isEmpty()) {
                return AsrResult.empty(cost(start));
            }
            return AsrResult.ok(String.join("", words), words, cost(start));

        } catch (Exception e) {
            log.error("百度 ASR 调用异常", e);
            return AsrResult.fail("调用识别服务失败：" + e.getMessage(), cost(start));
        }
    }

    /** 把官方错误码翻译成能直接照做的提示，而不是原样丢给用户 */
    private static String hintFor(int errNo) {
        return switch (errNo) {
            case 3300 -> "（音频质量有问题，检查是不是空的或者全是噪声）";
            case 3301 -> "（音频质量太差，识别不出来）";
            case 3302 -> "（鉴权失败，检查 API Key / Secret Key）";
            case 3303 -> "（识别出了但结果为空，可能是纯环境声）";
            case 3304 -> "（超出单次时长上限，最长 60 秒）";
            case 3305 -> "（音频格式不对，必须 16k 单声道 16 位 WAV）";
            case 3307 -> "（音频过长或音量太小）";
            case 3308 -> "（音频有质量问题，可能是采样率不对）";
            case 3309 -> "（音频体积过大）";
            case 3310 -> "（音频时长或音量不合格）";
            case 3311 -> "（采样率不是 16k）";
            case 3312 -> "（音频格式不对）";
            case 3313 -> "（百度内部服务错误，重试即可）";
            case 3314 -> "（每天调用量已达上限）";
            case 3315 -> "（未授权或额度用尽）";
            case 3316 -> "（频率超限，慢一点）";
            case 3317 -> "（音频数据长度为 0）";
            case 3319 -> "（日调用量超限）";
            case 3320 -> "（免费额度已用尽）";
            default -> "";
        };
    }

    private String accessToken() throws Exception {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
                return cachedToken;
            }
            String raw = client.post()
                    .uri(TOKEN_URL + "?grant_type=client_credentials&client_id={id}&client_secret={secret}",
                            Map.of("id", apiKey, "secret", secretKey))
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(raw);
            if (!node.hasNonNull("access_token")) {
                String desc = node.path("error_description").asText(raw);
                throw new IllegalStateException("拿不到 access_token：" + desc);
            }
            cachedToken = node.get("access_token").asText();
            // 官方有效期 30 天，保守按 25 天缓存
            tokenExpireAt = System.currentTimeMillis() + Duration.ofDays(25).toMillis();
            log.info("已获取百度 access_token，25 天后自动续期");
            return cachedToken;
        }
    }

    private static int cost(long start) {
        return (int) (System.currentTimeMillis() - start);
    }
}
