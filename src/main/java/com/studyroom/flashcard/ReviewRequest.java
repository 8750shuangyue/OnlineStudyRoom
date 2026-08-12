package com.studyroom.flashcard;

import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
        @NotNull(message = "请选择复习结果")
        FlashcardRating rating) {
}
