package com.starmuseum.web.dto;

import java.util.List;

/**
 * 对前端的输出形状。刻意和前端 `src/data/seed.js` 的结构保持一致 ——
 * 前端从「本地种子数据」切到「后端接口」时，组件几乎不用改。
 */
public final class Dtos {

    private Dtos() {
    }

    public record ExhibitCard(Long id, String no, String title, String sign, String place,
                              String date, int seconds, List<Double> trace, String audioUrl,
                              long echoCount, boolean seed) {
    }

    public record ExhibitDetail(Long id, String no, String title, String sign, String full,
                                String place, String date, int seconds, List<Double> trace,
                                String audioUrl, String keeper, long echoCount, boolean seed,
                                String status, String auditNote) {
    }

    /**
     * 「我的声音」里，我埋下的那几段。
     *
     * 带状态：投稿的人有权知道自己那一段现在在哪 ——
     * 是已经入馆，还是在等人工看一眼，还是没通过、为什么。
     */
    public record MyUpload(String no, String title, String status, String auditNote,
                           String date, int seconds, String audioUrl, String createdAt,
                           long echoCount) {
    }

    public record HeritageCard(Long id, String slug, String archiveNo, String title, String era,
                               String note, String source, String license, int seconds,
                               List<Double> trace, String audioUrl) {
    }

    public record EchoView(Long id, String author, String kind, String body, String audioUrl,
                           Integer seconds, List<Double> trace, String createdAt) {
    }

    public record SessionView(Long id, String nickname) {
    }

    public record DrawResult(String type, ExhibitCard exhibit, HeritageCard heritage) {
    }

    public record PendingItem(Long id, String type, String title, String text, String reason,
                              String createdAt) {
    }

    public record SignTrace(String sign, String provider, boolean guarded, boolean fellBack,
                            List<String> violations) {
    }

    public record Stats(long exhibitCount, long heritageCount, long reviewCount,
                        long echoCount, String signProvider) {
    }
}
