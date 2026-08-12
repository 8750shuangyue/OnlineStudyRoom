package com.studyroom.gamification;

import java.util.List;

public record AchievementResponse(
        UserStatsResponse stats,
        List<BadgeInfo> badges,
        List<TitleInfo> titles) {
}
