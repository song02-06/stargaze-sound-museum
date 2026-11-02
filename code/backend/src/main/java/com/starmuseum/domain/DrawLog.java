package com.starmuseum.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 打捞记录。两个用途：
 * 1）「本轮不重复」——同一轮里不把刚捞过的东西再给一次；
 * 2）彩蛋保底 —— 新用户前 N 次必出一次历史之声，没有这张表就没法判断。
 */
@Entity
@Table(name = "draw_log", indexes = @Index(name = "idx_draw_session", columnList = "sessionId,createdAt"))
public class DrawLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected DrawLog() {
    }

    public DrawLog(Long sessionId, TargetType targetType, Long targetId) {
        this.sessionId = sessionId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
