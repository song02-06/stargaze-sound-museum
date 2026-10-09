package com.yourname.museum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 历史声音彩蛋。 */
@Data
@Entity
@Table(name = "heritage_sound")
public class HeritageSound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 40)
    private String speaker;

    @Column(length = 20)
    private String era;

    @Column(length = 20)
    private String country;

    @Column(length = 255)
    private String audioPath;

    /** 背后故事（依据公开史料，必须注明来源） */
    @Column(length = 500)
    private String story;

    @Column(length = 120)
    private String sourceName;

    @Column(length = 255)
    private String sourceUrl;

    /** 公版 / 国家档案 / 仅供教学 */
    @Column(length = 120)
    private String licenseNote;

    private Integer weight = 30;

    private Boolean enabled = Boolean.TRUE;
}
