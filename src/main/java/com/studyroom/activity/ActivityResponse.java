package com.studyroom.activity;

import java.time.LocalDateTime;

public record ActivityResponse(
        Long id,
        String username,
        String type,
        String text,
        LocalDateTime createdAt) {

    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(activity.getId(), activity.getUsername(),
                activity.getType(), activity.getText(), activity.getCreatedAt());
    }
}
