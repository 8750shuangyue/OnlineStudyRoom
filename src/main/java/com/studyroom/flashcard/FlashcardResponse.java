package com.studyroom.flashcard;

import java.time.LocalDateTime;

public record FlashcardResponse(
        Long id,
        String front,
        String back,
        int reviewCount,
        LocalDateTime dueAt,
        LocalDateTime lastReviewedAt,
        String sourceType,
        Long sourceId,
        boolean due) {

    public static FlashcardResponse from(Flashcard card) {
        return new FlashcardResponse(card.getId(), card.getFront(), card.getBack(),
                card.getReviewCount(), card.getDueAt(), card.getLastReviewedAt(),
                card.getSourceType(), card.getSourceId(),
                card.getDueAt() != null && !card.getDueAt().isAfter(LocalDateTime.now()));
    }
}
