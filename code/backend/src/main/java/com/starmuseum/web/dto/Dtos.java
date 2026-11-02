package com.starmuseum.web.dto;

import java.util.List;

/**
 * 对前端的输出形状。刻意和前端 `src/data/seed.js` 的结构保持一致 ——
 * 前端从「本地种子数据」切到「后端接口」时，组件几乎不用改。
 *
 * 所有新字段一律**追加在末尾**：前端老代码读不到新字段不会炸，
 * 新代码能用上新字段，滚动升级不会互相卡住。
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
                                String status, String auditNote, String soundType, String sourceUrl, String sourceAuthor, String sourceLicense, String sourceDateText) {
    }

    /**
     * 「我的声音」里，我埋下的那几段。
     *
     * 带状态：投稿的人有权知道自己那一段现在在哪 ——
     * 是已经入馆，还是在等人工看一眼，还是没通过、为什么。
     *
     * echoCount 是这件展品下面**全部**回音（含待审、被驳回）：
     * 删展品时它们会一起消失，只报公开条数会让人低估后果。
     */
    public record MyUpload(String no, String title, String status, String auditNote,
                           String date, int seconds, String audioUrl, String createdAt,
                           long echoCount, long publicEchoCount, String soundType,
                           String updatedAt) {
    }

    /** 编辑投稿时要回填的原始字段。只有原作者能拿到 */
    public record UploadSource(String no, String title, String full, String place, String date,
                               String soundType, boolean originalConfirmed, String status,
                               String auditNote, String audioUrl, int seconds) {
    }

    public record HeritageCard(Long id, String slug, String archiveNo, String title, String era,
                               String note, String source, String license, int seconds,
                               List<Double> trace, String audioUrl) {
    }

    /**
     * 回音。公开列表只会有 PASS 的那几条；
     * 但发送人自己需要看到「它现在是什么状态、为什么」——
     * 所以 status / auditNote 一起返回，前端按场景选择显示。
     */
    public record EchoView(Long id, String author, String kind, String body, String audioUrl,
                           Integer seconds, List<Double> trace, String createdAt,
                           String status, String auditNote, Long authorId) {
    }

    /** 「我发出的回音」：多带展品编号，才点得回去 */
    public record MyEcho(Long id, String exhibitNo, String exhibitTitle, String kind, String body,
                         String audioUrl, Integer seconds, String status, String auditNote,
                         String createdAt) {
    }

    public record SessionView(Long id, String nickname) {
    }

    public record DrawResult(String type, ExhibitCard exhibit, HeritageCard heritage) {
    }

    /**
     * 管理端待复审项。
     *
     * audioUrl / seconds 是必须的：语音回音转写失败时会被送到这里，
     * 管理员**只能靠听**下判断。没有播放入口的队列等于没法审。
     */
    public record PendingItem(Long id, String type, String kind, String title, String text,
                              String reason, String place, String date, String audioUrl,
                              Integer seconds, String createdAt) {
    }

    /** 收件箱：我的展品收到的公开回音 */
    public record InboxEcho(Long echoId, String exhibitNo, String exhibitTitle, String author,
                            Long authorId, String kind, String body, String audioUrl,
                            Integer seconds, List<Double> trace, String createdAt) {
    }

    public record NotificationView(Long id, String kind, String targetType, String targetId,
                                   String targetNo, String summary, String createdAt,
                                   boolean read, String readAt) {
    }

    public record Notifications(List<NotificationView> items, long unread) {
    }

    public record UnreadCount(long unread) {
    }

    public record SignTrace(String sign, String provider, boolean guarded, boolean fellBack,
                            List<String> violations) {
    }

    public record Stats(long exhibitCount, long heritageCount, long reviewCount,
                        long echoCount, String signProvider) {
    }
}
