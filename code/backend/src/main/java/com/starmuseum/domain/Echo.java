package com.starmuseum.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 回音 —— 产品的核心差异化：回应可以是文字，也可以是一段自己的声音。
 * 回音默认公开；收件箱只是「关于我的声音的所有回音」的汇总视图，不是私信。
 */
@Entity
@Table(name = "echo", indexes = @Index(name = "idx_echo_exhibit", columnList = "exhibitId"))
public class Echo {

    public enum Kind { TEXT, VOICE }

    public enum AuditStatus { PENDING, PASS, REVIEW, REJECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long exhibitId;

    private Long authorId;

    @Column(nullable = false, length = 12)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    /** 文字回音的正文；语音回音为空，正文来自 ASR 转写（仅供审核与搜索） */
    @Lob
    private String body;

    @Column(length = 200)
    private String audioPath;

    private Integer seconds;

    @Lob
    private String trace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AuditStatus status = AuditStatus.PENDING;

    @Column(length = 200)
    private String auditReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean seeded;

    protected Echo() {
    }

    public Echo(Long exhibitId, Long authorId, String authorName, Kind kind, String body,
                String audioPath, Integer seconds, String trace, boolean seeded) {
        this.exhibitId = exhibitId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.kind = kind;
        this.body = body;
        this.audioPath = audioPath;
        this.seconds = seconds;
        this.trace = trace;
        this.seeded = seeded;
        this.status = AuditStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void approve() {
        this.status = AuditStatus.PASS;
        this.auditReason = null;
    }

    public void sendToReview(String reason) {
        this.status = AuditStatus.REVIEW;
        this.auditReason = reason;
    }

    public void reject(String reason) {
        this.status = AuditStatus.REJECT;
        this.auditReason = reason;
    }

    /**
     * 仅用于演示数据：把时间往前挪，让「最近入馆」看起来像真的在积累。
     * 真实投稿永远走构造函数里的 now()。
     */
    public void backdateTo(LocalDateTime at) {
        this.createdAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getExhibitId() {
        return exhibitId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Kind getKind() {
        return kind;
    }

    public String getBody() {
        return body;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public Integer getSeconds() {
        return seconds;
    }

    public String getTrace() {
        return trace;
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
