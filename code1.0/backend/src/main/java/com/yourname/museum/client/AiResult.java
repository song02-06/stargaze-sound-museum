package com.yourname.museum.client;

/**
 * 一次调用同时完成「润色 + 标题 + 标签 + 情绪 + 适宜性判定」。
 * fallback=true 表示大模型不可用，已降级为原文入库。
 */
public record AiResult(
        String title,
        String story,
        String tags,
        String emotion,
        boolean safe,
        String level,
        String reason,
        boolean fallback,
        int costMs,
        String rawResponse
) {
    /**
     * 降级结果。
     *
     * <p>{@code costMs} 必须由调用方传入真实耗时：实验二要统计「AI 路的平均耗时」，
     * 如果降级时填 0，失败样本会把均值拉低，得出的结论就是错的。
     */
    public static AiResult fallback(String rawText, String error, int costMs) {
        String title = (rawText == null || rawText.isBlank())
                ? "未命名的声音"
                : rawText.substring(0, Math.min(12, rawText.length()));
        return new AiResult(title, rawText, "未分类", "平静", true, "LOW",
                "AI 不可用，已降级为原文入库：" + error, true, costMs, null);
    }
}
