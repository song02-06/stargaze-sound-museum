package com.yourname.museum.dto;

/**
 * 一次抽取的结果。type 只能是 BOTTLE 或 HERITAGE。
 * 前端按 type 决定是走普通播放还是「星轨汇聚」彩蛋动画。
 */
public record DrawItem(
        String type,
        Long id,
        String title,
        String story,
        String tags,
        String emotion,
        String audioUrl,
        Integer durationMs,
        String speaker,
        String era,
        String country,
        String sourceName,
        String sourceUrl,
        String licenseNote
) {
}
