package com.studyroom.mistake;

import java.time.LocalDateTime;

public record MistakeResponse(
        Long id,
        String subject,
        String question,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        MistakeReviewStatus reviewStatus,
        int reviewCount,
        LocalDateTime nextReviewAt,
        LocalDateTime lastReviewedAt) {
}
