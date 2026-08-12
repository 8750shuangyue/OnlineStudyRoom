package com.studyroom.realtime;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        Long roomId,
        String username,
        String content,
        LocalDateTime createdAt,
        List<String> mentions) {

    public static MessageResponse from(ChatMessage message) {
        return new MessageResponse(message.getId(), message.getRoomId(),
                message.getUsername(), message.getContent(), message.getCreatedAt(),
                message.getMentions() == null ? List.of() : message.getMentions());
    }
}
