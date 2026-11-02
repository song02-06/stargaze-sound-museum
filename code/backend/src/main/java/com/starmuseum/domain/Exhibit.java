package com.starmuseum.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 展品。四要素齐全才叫展品：声音、标题、故事、语境（时间 + 地点）。
 * 全文 fullText 永久保留，一字不改；signText 是从全文里做减法压出来的展签。
 *
 * 「永久保留」的准确含义是：**不被展签覆盖**、不被系统改写。
 * 用户自己主动编辑，是可以更新全文的（见 {@link #applyEdit}）。
 */
@Entity
@Table(name = "exhibit", indexes = {
        @Index(name = "idx_exhibit_status", columnList = "status"),
        @Index(name = "idx_exhibit_created", columnList = "createdAt")
})
public class Exhibit {

    public enum AuditStatus { PENDING, PASS, REVIEW, REJECT }

    /**
     * 投稿分类。演唱类（SINGING）按约定**强制人工复审** ——
     * 不依赖任何自动识别去判断「这到底是不是演唱」。
     */
    public enum SoundType { ENVIRONMENT, SPEECH, SINGING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 馆藏编号，形如 0247。由数据库序列单调分配，删除后不复用 */
    @Column(nullable = false, unique = true, length = 8)
    private String no;

    @Column(nullable = false, length = 80)
    private String title;

    /** 展签：30–60 字，从全文里做减法得来 */
    @Lob
    @Column(nullable = false)
    private String signText;

    /** 全文：用户手写，系统永不改写；用户主动编辑时可以更新 */
    @Lob
    @Column(nullable = false)
    private String fullText;

    @Column(nullable = false, length = 60)
    private String place;

    @Column(nullable = false)
    private LocalDate recordedOn;

    @Column(nullable = false, length = 200)
    private String audioPath;

    @Column(nullable = false)
    private int seconds;

    /** 波形：从真实音频采样里抽出来的点，逗号分隔 */
    @Lob
    private String trace;

    private Long keeperId;

    @Column(length = 12)
    private String keeperName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AuditStatus status = AuditStatus.PENDING;

    @Column(length = 200)
    private String auditReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 最后一次用户编辑的时间。没改过就是 null */
    private LocalDateTime updatedAt;

    /**
     * 投稿分类。列可为空只是为了兼容已有数据的加列：
     * 读的时候统一由 {@link #getSoundType()} 兜成 ENVIRONMENT。
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private SoundType soundType = SoundType.ENVIRONMENT;

    /** 原创声明：后端收到 false / 缺失时直接拒收，不会走到审核 */
    @Column
    private Boolean originalConfirmed = Boolean.FALSE;

    /** 演示数据标记 —— 上线前必须能一眼分辨哪些不是真用户留下的 */
    @Column(nullable = false)
    private boolean seeded;

    protected Exhibit() {
    }

    public Exhibit(String no, String title, String signText, String fullText, String place,
                   LocalDate recordedOn, String audioPath, int seconds, String trace,
                   Long keeperId, String keeperName, boolean seeded) {
        this.no = no;
        this.title = title;
        this.signText = signText;
        this.fullText = fullText;
        this.place = place;
        this.recordedOn = recordedOn;
        this.audioPath = audioPath;
        this.seconds = seconds;
        this.trace = trace;
        this.keeperId = keeperId;
        this.keeperName = keeperName;
        this.seeded = seeded;
        this.status = AuditStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    @Column(length = 500)
    private String audioReviewReason;

    public String getAudioReviewReason() { return audioReviewReason; }
    public void requireAudioReview(String reason) {
        this.audioReviewReason = reason;
        sendToReview(reason);
    }
    public void clearAudioReview() { this.audioReviewReason = null; }

    private String sourceUrl;
    private String sourceAuthor;
    private String sourceLicense;
    private String sourceDateText;
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceAuthor() { return sourceAuthor; }
    public String getSourceLicense() { return sourceLicense; }
    public String getSourceDateText() { return sourceDateText; }
    public void credit(String url, String author, String license, String dateText) {
        this.sourceUrl = url; this.sourceAuthor = author;
        this.sourceLicense = license; this.sourceDateText = dateText;
    }

    public void approve() {
        this.status = AuditStatus.PASS;
        this.auditReason = null;
    }

    /** 展签由 SignService 产出；它必须已经过 SignGuard 复核 */
    public void setSignText(String signText) {
        this.signText = signText;
    }

    /**
     * 用户自己提交的原始字段。
     * 投稿时写一次；之后只有 {@link #applyEdit} 会再动它们。
     */
    public void applySubmission(SoundType soundType, boolean originalConfirmed) {
        this.soundType = soundType == null ? SoundType.ENVIRONMENT : soundType;
        this.originalConfirmed = originalConfirmed;
    }

    /**
     * 用户编辑自己的投稿。
     *
     * 只动「用户写的东西」：标题、故事、地点、录制日期、分类、声明。
     * id / no / keeperId / audioPath / createdAt 一律不动 ——
     * 编辑是改这一件东西，不是再投一件。
     */
    public void applyEdit(String title, String fullText, String place, LocalDate recordedOn,
                          SoundType soundType, boolean originalConfirmed) {
        this.title = title;
        this.fullText = fullText;
        this.place = place;
        this.recordedOn = recordedOn;
        this.soundType = soundType == null ? SoundType.ENVIRONMENT : soundType;
        this.originalConfirmed = originalConfirmed;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 仅用于演示数据：让入库时间回到录制当天。
     * 否则「最近入馆」会按种子文件的书写顺序排，和日期对不上。
     * 真实投稿永远走构造函数里的 now()。
     */
    public void backdateTo(LocalDateTime at) {
        this.createdAt = at;
    }

    public void sendToReview(String reason) {
        this.status = AuditStatus.REVIEW;
        this.auditReason = reason;
    }

    public void reject(String reason) {
        this.status = AuditStatus.REJECT;
        this.auditReason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getNo() {
        return no;
    }

    public String getTitle() {
        return title;
    }

    public String getSignText() {
        return signText;
    }

    public String getFullText() {
        return fullText;
    }

    public String getPlace() {
        return place;
    }

    public LocalDate getRecordedOn() {
        return recordedOn;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public int getSeconds() {
        return seconds;
    }

    public String getTrace() {
        return trace;
    }

    public Long getKeeperId() {
        return keeperId;
    }

    public String getKeeperName() {
        return keeperName;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public String getAuditReason() {
        return auditReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public SoundType getSoundType() {
        return soundType == null ? SoundType.ENVIRONMENT : soundType;
    }

    /** 老数据没有这一列时按「未声明」处理，宁可保守 */
    public boolean isOriginalConfirmed() {
        return Boolean.TRUE.equals(originalConfirmed);
    }

    public boolean isSeeded() {
        return seeded;
    }
}
