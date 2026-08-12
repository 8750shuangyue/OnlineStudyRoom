package com.studyroom.study;

import java.time.LocalDateTime;

public record StudySessionResponse(
        Long id,
        Long roomId,
        String roomName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationSeconds,
        Long taskId,
        String reflection) {
}
