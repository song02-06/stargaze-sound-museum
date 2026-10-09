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

/** 普通用户留言瓶。 */
@Data
@Entity
@Table(name = "voice_bottle")
public class VoiceBottle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 相对路径，如 bottle/20260914_ab12cd34.wav */
    @Column(nullable = false, length = 255)
    private String audioPath;

    private Integer durationMs;

    /** ASR 原始结果 —— 必须保留，论文里做准确率对比要用 */
    @Lob
    private String rawTranscript;

    /** AI 润色后的故事（80–150 字） */
    @Lob
    private String polishedText;

    /** 用户自己写的那句话（润色前） */
    @Column(length = 200)
    private String originalNote;

    @Column(length = 50)
    private String title;

    /** 逗号分隔，如「童年,夏天,外婆」 */
    @Column(length = 120)
    private String tags;

    /** AI 判定的情绪标签，仅用于展示与筛选 */
    @Column(length = 20)
    private String emotion;

    /** PENDING / PASS / REVIEW / REJECT */
    @Column(length = 16)
    private String auditStatus;

    @Column(length = 255)
    private String auditReason;

    /** UPLOAD / TTS_SEED / SELF_RECORD */
    @Column(length = 16)
    private String source;

    private LocalDateTime createdAt;
}
