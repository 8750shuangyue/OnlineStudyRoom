package com.studyroom.stats;

import java.time.LocalDate;

public record WeeklyReport(
        long totalMinutes,
        long totalSessions,
        long daysActive,
        LocalDate bestDay,
        long bestDayMinutes) {
}
