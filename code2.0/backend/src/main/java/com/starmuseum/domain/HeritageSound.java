package com.starmuseum.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 历史之声。与用户投稿**分开陈列**，不混进同一个池子的展示逻辑里。
 *
 * source 和 license 是硬字段：博物馆的展品必须有出处。
 * 现在库里放的全是合成占位音频，所以 source 里写明了「非真实素材」。
 */
@Entity
@Table(name = "heritage_sound")
public class HeritageSound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 稳定标识，同时是音频文件名，如 1941-07 */
    @Column(nullable = false, unique = true, length = 32)
    private String slug;

    @Column(nullable = false, length = 40)
    private String archiveNo;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 60)
    private String era;

    @Lob
    private String note;

    @Column(nullable = false, length = 80)
    private String source;

    @Column(nullable = false, length = 40)
    private String license;

    @Column(nullable = false, length = 200)
    private String audioPath;

    @Column(nullable = false)
    private int seconds;

    @Lob
    private String trace;

    /** 加权随机的权重 */
    @Column(nullable = false)
    private int weight = 10;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean seeded;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected HeritageSound() {
    }

    public HeritageSound(String slug, String archiveNo, String title, String era, String note,
                         String source, String license, String audioPath, int seconds,
                         String trace, int weight, int sortOrder, boolean seeded) {
        this.slug = slug;
        this.archiveNo = archiveNo;
        this.title = title;
        this.era = era;
        this.note = note;
        this.source = source;
        this.license = license;
        this.audioPath = audioPath;
        this.seconds = seconds;
        this.trace = trace;
        this.weight = weight;
        this.sortOrder = sortOrder;
        this.seeded = seeded;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getArchiveNo() {
        return archiveNo;
    }

    public String getTitle() {
        return title;
    }

    public String getEra() {
        return era;
    }

    public String getNote() {
        return note;
    }

    public String getSource() {
        return source;
    }

    public String getLicense() {
        return license;
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

    public int getWeight() {
        return weight;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isSeeded() {
        return seeded;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
