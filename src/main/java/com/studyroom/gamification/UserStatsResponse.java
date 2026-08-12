package com.studyroom.gamification;

public record UserStatsResponse(
        long xp,
        int level,
        long xpIntoLevel,
        long xpNeededForNext,
        int streak,
        int bestStreak,
        long totalSessions,
        long totalMinutes,
        long todayMinutes,
        long distinctDays) {
}
