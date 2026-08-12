package com.studyroom.task;

import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String title,
        boolean done,
        LocalDateTime createdAt,
        LocalDateTime completedAt) {
}
