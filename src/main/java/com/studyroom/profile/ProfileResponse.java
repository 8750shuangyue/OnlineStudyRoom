package com.studyroom.profile;

import com.studyroom.gamification.BadgeInfo;
import com.studyroom.gamification.TitleInfo;
import com.studyroom.gamification.UserStatsResponse;

import java.util.List;

public record ProfileResponse(
        String username,
        UserStatsResponse stats,
        List<BadgeInfo> badges,
        List<TitleInfo> titles,
        List<RecentSession> recentSessions) {
}
