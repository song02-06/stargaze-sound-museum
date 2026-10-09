package com.yourname.museum.dto;

import com.yourname.museum.entity.VoiceBottle;

import java.time.LocalDateTime;

public record BottleDetail(
        Long id,
        String title,
        String originalNote,
        String rawTranscript,
        String polishedText,
        String tags,
        String emotion,
        String auditStatus,
        String auditReason,
        String source,
        String audioUrl,
        Integer durationMs,
        LocalDateTime createdAt
) {
    public static BottleDetail of(VoiceBottle b) {
        return new BottleDetail(
                b.getId(), b.getTitle(), b.getOriginalNote(), b.getRawTranscript(),
                b.getPolishedText(), b.getTags(), b.getEmotion(), b.getAuditStatus(),
                b.getAuditReason(), b.getSource(),
                b.getAudioPath() == null ? null : "/audio/" + b.getAudioPath(),
                b.getDurationMs(), b.getCreatedAt());
    }
}
