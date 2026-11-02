package com.starmuseum.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 一条「给某个人的消息」。
 *
 * 产品里没有私信，这里存的也不是聊天记录 —— 它只是一条**状态变化的回执**：
 * 「你投的那段通过了」「你的展品收到一条回音」「你发的那条回音审核完了」。
 * 收件箱展示的内容仍然是公开回音的汇总，不改变「没有私信」这个定义。
 *
 * 存数据库而不是走 WebSocket：换个浏览器、隔一天再打开，消息还在。
 */
@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipientSessionId"),
        @Index(name = "idx_notification_target", columnList = "targetType,targetId")
})
public class Notification {

    public enum Kind {
        /** 我的展品收到一条已公开的回音 */
        ECHO_RECEIVED,
        /** 我的投稿审核完了 */
        EXHIBIT_REVIEW_RESULT,
        /** 我发出的回音审核完了 */
        ECHO_REVIEW_RESULT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 收消息的人。只按会话认人 —— 和投稿、回音的所有权规则一致 */
    @Column(nullable = false)
    private Long recipientSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Kind kind;

    /** EXHIBIT / ECHO，指的是这条消息**要跳到哪里去**的对象类型 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TargetType targetType;

    /** 目标对象的自增 id（说明消息指向哪一条记录） */
    private Long targetId;

    /**
     * 目标对象的展示编号。
     * 展品用四位编号拼 URL；回音用它所属的展品编号。
     * 光有自增 id 前端是跳不过去的。
     */
    @Column(length = 8)
    private String targetNo;

    @Column(length = 200)
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 读过的时间。null = 未读 */
    private LocalDateTime readAt;

    /** 目标对象没了（展品被删）时置为失效，不再出现在收件箱里 */
    @Column(nullable = false)
    private boolean targetGone;

    protected Notification() {
    }

    public Notification(Long recipientSessionId, Kind kind, TargetType targetType,
                        Long targetId, String targetNo, String summary) {
        this.recipientSessionId = recipientSessionId;
        this.kind = kind;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetNo = targetNo;
        this.summary = summary;
        this.createdAt = LocalDateTime.now();
        this.targetGone = false;
    }

    public void markRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }

    public void markTargetGone() {
        this.targetGone = true;
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientSessionId() {
        return recipientSessionId;
    }

    public Kind getKind() {
        return kind;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getTargetNo() {
        return targetNo;
    }

    public String getSummary() {
        return summary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isTargetGone() {
        return targetGone;
    }
}
