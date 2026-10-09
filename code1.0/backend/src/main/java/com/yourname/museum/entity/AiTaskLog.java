package com.yourname.museum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 调用日志 —— 记录降级与耗时，答辩时是「有工程意识」的证据。 */
@Data
@Entity
@Table(name = "ai_task_log")
public class AiTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bottleId;

    @Column(length = 32)
    private String provider;

    @Column(length = 64)
    private String model;

    private Integer promptChars;

    private Integer outputChars;

    private Integer costMs;

    private Boolean success;

    /** 是否走了降级（直接用原文） */
    private Boolean fallback;

    @Lob
    private String rawResponse;

    private LocalDateTime createdAt;
}
