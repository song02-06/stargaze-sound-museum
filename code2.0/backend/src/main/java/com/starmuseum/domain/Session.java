package com.starmuseum.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 轻身份：昵称 + 本地密钥。
 *
 * 没有邮箱、没有密码、没有头像 —— 用户在产品定义里被承诺了
 * 「你在馆里有个名字，但没人知道你是谁」。
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
