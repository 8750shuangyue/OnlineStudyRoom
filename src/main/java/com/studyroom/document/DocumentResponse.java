package com.studyroom.document;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        String name,
        String category,
        int charCount,
        LocalDateTime createdAt) {
}
