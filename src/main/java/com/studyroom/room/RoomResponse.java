package com.studyroom.room;

import java.time.LocalDateTime;

public record RoomResponse(
        Long id,
        String name,
        String category,
        boolean hasPassword,
        String ownerUsername,
        long memberCount,
        LocalDateTime createdAt,
        Integer weeklyGoalMinutes) {
}
