package com.studyroom.stats;

public record StatsResponse(
        long totalSessions,
        long totalDurationSeconds,
        long todaySessions,
        long todayDurationSeconds) {
}
