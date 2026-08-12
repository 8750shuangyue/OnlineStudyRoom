package com.studyroom.room;

import java.time.LocalDateTime;

public record RoomInviteResponse(
        Long id,
        Long roomId,
        String roomName,
        String fromUsername,
        LocalDateTime createdAt) {
}
