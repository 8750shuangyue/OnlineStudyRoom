package com.studyroom.profile;

import com.studyroom.gamification.BadgeInfo;
import com.studyroom.gamification.SeasonAwardInfo;
import com.studyroom.gamification.TitleInfo;
import com.studyroom.gamification.UserStatsResponse;

import java.time.LocalDateTime;
import java.util.List;

/** 公开名片：无需登录即可查看的轻量用户信息。 */
public record CardResponse(
        String username,
        LocalDateTime createdAt,
        UserStatsResponse stats,
        List<BadgeInfo> badges,
        List<TitleInfo> titles,
        List<SeasonAwardInfo> seasonAwards) {
}
