package com.studyroom.gamification;

public record SeasonProgress(
        String seasonKey,
        long minutes,
        long sessions,
        long days) {
}
