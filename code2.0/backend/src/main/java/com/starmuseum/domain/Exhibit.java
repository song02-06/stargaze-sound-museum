package com.starmuseum.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 展品。四要素齐全才叫展品：声音、标题、故事、语境（时间 + 地点）。
 * 全文 fullText 永久保留，一字不改；signText 是从全文里做减法压出来的展签。
 */
@Entity
@Table(name = "exhibit", indexes = {
        @Index(name = "idx_exhibit_status", columnList = "status"),
        @Index(name = "idx_exhibit_created", columnList = "createdAt")
})
public class Exhibit {

    public enum AuditStatus { PENDING, PASS, REVIEW, REJECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 馆藏编号，形如 0247 */
    @Column(nullable = false, unique = true, length = 8)
    private String no;

    @Column(nullable = false, length = 80)
    private String title;

    /** 展签：30–60 字，从全文里做减法得来 */
    @Lob
    @Column(nullable = false)
    private String signText;

    /** 全文：用户手写，永久保留 */
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

    public void approve() {
        this.status = AuditStatus.PASS;
        this.auditReason = null;
    }

    /** 展签由 SignService 产出；它必须已经过 SignGuard 复核 */
    public void setSignText(String signText) {
        this.signText = signText;
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

    public boolean isSeeded() {
        return seeded;
    }
}
