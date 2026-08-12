package com.studyroom.realtime;

import java.time.LocalDateTime;

public record MentionNotification(
        Long roomId,
        String roomName,
        String fromUsername,
        String content,
        LocalDateTime createdAt) {
}
