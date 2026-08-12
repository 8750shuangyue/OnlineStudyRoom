package com.studyroom.profile;

import java.time.LocalDateTime;

public record RecentSession(
        Long id,
        String roomName,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationSeconds,
        String reflection) {
}
