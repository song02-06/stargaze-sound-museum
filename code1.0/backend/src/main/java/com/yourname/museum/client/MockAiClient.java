package com.yourname.museum.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Random;

/** 本地模板润色：不联网、不花钱，用来跑通「上传 → 润色 → 入馆」。 */
@Service
@ConditionalOnProperty(name = "museum.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private static final String[] EMOTIONS = {"怀念", "温柔", "遗憾", "释然", "平静"};
    private static final String[] TAGS = {"旧时光", "家乡", "离别", "成长", "深夜"};

    private final Random random = new Random();

    @Override
    public AiResult polish(String rawText, String originalNote) {
        long start = System.currentTimeMillis();
        String base = (rawText == null || rawText.isBlank()) ? originalNote : rawText;
        if (base == null) {
            base = "";
        }
        String title = base.isBlank() ? "未命名的声音" : base.substring(0, Math.min(10, base.length()));
        String story = "那一段被留下的声音里，有人轻轻地说了这样一件事：" + trimTail(base)
                + "。声音落下的时候，像是有人把一小片天空，折起来放进了口袋里。";
        String emotion = EMOTIONS[random.nextInt(EMOTIONS.length)];
        String tags = TAGS[random.nextInt(TAGS.length)] + "," + TAGS[random.nextInt(TAGS.length)];

        // 演示开关：转写文本或用户补充的那句话里命中「测试违规」就判高风险，
        // 方便现场演示 AI 路的拦截（语音转写结果不可控，所以两处都要看）。
        String combined = base + " " + (originalNote == null ? "" : originalNote);
        boolean safe = !combined.contains("测试违规");
        return new AiResult(title, story, tags, emotion, safe, safe ? "LOW" : "HIGH",
                safe ? "内容适合公开展示" : "疑似不适宜内容",
                false, (int) (System.currentTimeMillis() - start), null);
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String modelName() {
        return "mock-template-v1";
    }

    /** 原文结尾多半已经带标点，先去掉再拼接，避免出现「。。」。 */
    private static String trimTail(String text) {
        String s = text == null ? "" : text.trim();
        while (!s.isEmpty() && "。！？!?，,、；;：: ".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
