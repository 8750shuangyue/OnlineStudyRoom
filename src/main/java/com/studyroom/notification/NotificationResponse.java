package com.studyroom.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        Long roomId,
        String link,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(),
                notification.getTitle(), notification.getBody(), notification.getRoomId(),
                notification.getLink(), notification.isRead(), notification.getCreatedAt());
    }
}
