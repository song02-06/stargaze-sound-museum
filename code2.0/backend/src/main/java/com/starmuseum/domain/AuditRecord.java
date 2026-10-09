package com.starmuseum.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 审核流水。每一路都写一条 —— 这张表同时是论文里审核对比实验的数据源。
 * costMs 是必须的：没有耗时，就没法比较规则路和 AI 路的成本。
 */
@Entity
@Table(name = "audit_record", indexes = {
        @Index(name = "idx_audit_target", columnList = "targetType,targetId"),
        @Index(name = "idx_audit_created", columnList = "createdAt")
})
public class AuditRecord {

    /** SIGN = 展签压缩；ASR = 语音转写。两者都进这张表，成本才看得全。 */
    public enum Stage { RULE, AI, MANUAL, SIGN, ASR }

    public enum Verdict { PASS, REVIEW, REJECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Verdict verdict;

    /** 命中词、判定理由、或者 SignGuard 报出的违规项 */
    @Lob
    private String detail;

    @Column(nullable = false)
    private long costMs;

    @Column(length = 24)
    private String operator;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AuditRecord() {
    }

    public AuditRecord(TargetType targetType, Long targetId, Stage stage, Verdict verdict,
                       String detail, long costMs, String operator) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.stage = stage;
        this.verdict = verdict;
        this.detail = detail;
        this.costMs = costMs;
        this.operator = operator;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Stage getStage() {
        return stage;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getDetail() {
        return detail;
    }

    public long getCostMs() {
        return costMs;
    }

    public String getOperator() {
        return operator;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
