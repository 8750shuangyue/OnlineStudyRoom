package com.studyroom.gamification;

import java.time.LocalDateTime;

public record SeasonAwardInfo(
        String code,
        String name,
        String description,
        String seasonKey,
        LocalDateTime earnedAt,
        String extra) {
}
