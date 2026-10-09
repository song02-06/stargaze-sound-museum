package com.yourname.museum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 审核流水 —— 论文「实验二：审核策略对比」的数据来源。 */
@Data
@Entity
@Table(name = "audit_record")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32)
    private String targetType;

    private Long targetId;

    /** RULE / AI / MANUAL */
    @Column(length = 16)
    private String stage;

    /** PASS / REVIEW / REJECT */
    @Column(length = 16)
    private String result;

    @Column(length = 255)
    private String hitWords;

    @Column(length = 16)
    private String aiLevel;

    @Column(length = 255)
    private String aiReason;

    @Column(length = 32)
    private String operator;

    /** 该路耗时（毫秒） */
    private Integer costMs;

    private LocalDateTime createdAt;
}
