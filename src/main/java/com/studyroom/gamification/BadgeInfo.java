package com.studyroom.gamification;

import java.time.LocalDateTime;

public record BadgeInfo(
        String code,
        String name,
        String description,
        boolean earned,
        LocalDateTime earnedAt) {
}
