package com.studyroom.note;

import java.time.LocalDateTime;
import java.util.List;

public record NoteResponse(
        Long id,
        String title,
        String category,
        List<String> tags,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
