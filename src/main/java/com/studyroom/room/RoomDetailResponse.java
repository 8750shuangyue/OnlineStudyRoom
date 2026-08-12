package com.studyroom.room;

import java.time.LocalDateTime;
import java.util.List;

public record RoomDetailResponse(
        Long id,
        String name,
        String category,
        String announcement,
        boolean hasPassword,
        String ownerUsername,
        long memberCount,
        LocalDateTime createdAt,
        List<String> members,
        int focusMinutes,
        int breakMinutes) {
}
