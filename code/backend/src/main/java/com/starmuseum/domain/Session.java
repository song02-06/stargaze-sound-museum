package com.starmuseum.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 轻身份：昵称 + 本地密钥。
 *
 * 游客可升级为用户名密码账号；升级保留原有内容关联。
 * deviceKey 由客户端生成并保管，服务端只认这个 key。
 */
@Entity
@Table(name = "museum_session")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String deviceKey;

    @Column(nullable = false, length = 12)
    private String nickname;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(unique = true, length = 24)
    private String username;

    @Column(length = 200)
    private String passwordHash;

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }

    public void register(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        // 游客密钥不能继续取回已注册账号。
        this.deviceKey = java.util.UUID.randomUUID().toString();
    }

    protected Session() {
    }

    public Session(String deviceKey, String nickname) {
        this.deviceKey = deviceKey;
        this.nickname = nickname;
        this.createdAt = LocalDateTime.now();
        this.lastSeenAt = this.createdAt;
    }

    public void touch() {
        this.lastSeenAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }
}
