package com.studyroom.gamification;

import com.studyroom.study.StudySession;
import com.studyroom.study.StudySessionRepository;
import com.studyroom.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.studyroom.activity.ActivityService;

@Service
public class GamificationService {

    private static final long XP_PER_LEVEL = 100;

    private static final Map<String, String> BADGE_NAMES = Map.ofEntries(
            Map.entry("FIRST_FOCUS", "初次专注"),
            Map.entry("SESSIONS_10", "渐入佳境"),
            Map.entry("SESSIONS_50", "持之以恒"),
            Map.entry("MINUTES_100", "百炼成钢"),
            Map.entry("MINUTES_500", "五百里路"),
            Map.entry("MINUTES_1000", "千里之行"),
            Map.entry("STREAK_3", "小试牛刀"),
            Map.entry("STREAK_7", "一周不辍"),
            Map.entry("STREAK_30", "铁人自律"),
            Map.entry("DAYS_7", "七日之约"),
            Map.entry("MARATHON", "马拉松选手"),
            Map.entry("NIGHT_OWL", "夜猫子"));

    private final UserStatsRepository userStatsRepository;
    private final BadgeRepository badgeRepository;
    private final StudySessionRepository studySessionRepository;
    private final ActivityService activityService;

    public GamificationService(UserStatsRepository userStatsRepository,
                               BadgeRepository badgeRepository,
                               StudySessionRepository studySessionRepository,
                               ActivityService activityService) {
        this.userStatsRepository = userStatsRepository;
        this.badgeRepository = badgeRepository;
        this.studySessionRepository = studySessionRepository;
        this.activityService = activityService;
    }

    @Transactional
    public void recordSessionCompleted(User user, StudySession session) {
        UserStats stats = getOrCreateStats(user);
        LocalDate today = LocalDate.now();
        LocalDate last = stats.getLastFocusDate();
        if (last == null) {
            stats.setStreak(1);
            stats.setDistinctDays(1);
        } else if (last.equals(today)) {
            // 同一天多次专注，不重复累计 streak
        } else if (last.equals(today.minusDays(1))) {
            stats.setStreak(stats.getStreak() + 1);
            stats.setDistinctDays(stats.getDistinctDays() + 1);
        } else {
            stats.setStreak(1);
            stats.setDistinctDays(stats.getDistinctDays() + 1);
        }
        stats.setLastFocusDate(today);
        stats.setBestStreak(Math.max(stats.getBestStreak(), stats.getStreak()));
        long minutes = Math.max(1, session.getDurationSeconds() / 60);
        stats.setXp(stats.getXp() + 10 + minutes);
        userStatsRepository.save(stats);
        awardBadges(user, stats, session);
    }

    @Transactional(readOnly = true)
    public AchievementResponse achievements(User user) {
        UserStats stats = getOrCreateStats(user);
        return new AchievementResponse(toStatsResponse(user, stats),
                evaluateBadges(user, stats),
                evaluateTitles(stats));
    }

    @Transactional
    public UserStats getOrCreateStats(User user) {
        return userStatsRepository.findByUserId(user.getId()).orElseGet(() -> {
            UserStats stats = new UserStats();
            stats.setUser(user);
            stats.setXp(0);
            stats.setStreak(0);
            stats.setBestStreak(0);
            stats.setDistinctDays(0);
            return userStatsRepository.save(stats);
        });
    }

    private UserStatsResponse toStatsResponse(User user, UserStats stats) {
        long totalMinutes = studySessionRepository.totalDurationSecondsByUserId(user.getId()) / 60;
        long todayMinutes = studySessionRepository
                .totalDurationSecondsByUserIdSince(user.getId(), LocalDate.now().atStartOfDay()) / 60;
        long totalSessions = studySessionRepository.countByUserId(user.getId());
        long xp = stats.getXp();
        int level = (int) (xp / XP_PER_LEVEL) + 1;
        return new UserStatsResponse(xp, level, xp % XP_PER_LEVEL, XP_PER_LEVEL,
                stats.getStreak(), stats.getBestStreak(), totalSessions, totalMinutes,
                todayMinutes, stats.getDistinctDays());
    }

    private void awardBadges(User user, UserStats stats, StudySession session) {
        long totalSessions = studySessionRepository.countByUserId(user.getId());
        long totalMinutes = studySessionRepository.totalDurationSecondsByUserId(user.getId()) / 60;
        boolean marathon = session.getDurationSeconds() >= 7200;
        boolean nightOwl = session.getEndedAt() != null
                && (session.getEndedAt().getHour() >= 22 || session.getEndedAt().getHour() < 5);

        Map<String, Boolean> conditions = new HashMap<>();
        conditions.put("FIRST_FOCUS", totalSessions >= 1);
        conditions.put("SESSIONS_10", totalSessions >= 10);
        conditions.put("SESSIONS_50", totalSessions >= 50);
        conditions.put("MINUTES_100", totalMinutes >= 100);
        conditions.put("MINUTES_500", totalMinutes >= 500);
        conditions.put("MINUTES_1000", totalMinutes >= 1000);
        conditions.put("STREAK_3", stats.getStreak() >= 3);
        conditions.put("STREAK_7", stats.getStreak() >= 7);
        conditions.put("STREAK_30", stats.getStreak() >= 30);
        conditions.put("DAYS_7", stats.getDistinctDays() >= 7);
        conditions.put("MARATHON", marathon);
        conditions.put("NIGHT_OWL", nightOwl);

        conditions.forEach((code, met) -> {
            if (met && badgeRepository.findByUserIdAndCode(user.getId(), code).isEmpty()) {
                Badge badge = new Badge();
                badge.setUser(user);
                badge.setCode(code);
                badge.setEarnedAt(LocalDateTime.now());
                badgeRepository.save(badge);
                activityService.record(user.getId(), user.getUsername(), "BADGE_EARNED",
                        "解锁徽章「" + badgeName(code) + "」");
            }
        });
    }

    private String badgeName(String code) {
        return BADGE_NAMES.getOrDefault(code, code);
    }

    private List<BadgeInfo> evaluateBadges(User user, UserStats stats) {
        Map<String, LocalDateTime> earned = new HashMap<>();
        badgeRepository.findByUserId(user.getId())
                .forEach(b -> earned.put(b.getCode(), b.getEarnedAt()));

        List<BadgeInfo> badges = new ArrayList<>(List.of(
                new BadgeInfo("FIRST_FOCUS", "初次专注", "完成第一次专注", false, null),
                new BadgeInfo("SESSIONS_10", "渐入佳境", "累计完成 10 次专注", false, null),
                new BadgeInfo("SESSIONS_50", "持之以恒", "累计完成 50 次专注", false, null),
                new BadgeInfo("MINUTES_100", "百炼成钢", "累计专注 100 分钟", false, null),
                new BadgeInfo("MINUTES_500", "五百里路", "累计专注 500 分钟", false, null),
                new BadgeInfo("MINUTES_1000", "千里之行", "累计专注 1000 分钟", false, null),
                new BadgeInfo("STREAK_3", "小试牛刀", "连续 3 天专注", false, null),
                new BadgeInfo("STREAK_7", "一周不辍", "连续 7 天专注", false, null),
                new BadgeInfo("STREAK_30", "铁人自律", "连续 30 天专注", false, null),
                new BadgeInfo("DAYS_7", "七日之约", "累计 7 天有专注记录", false, null),
                new BadgeInfo("MARATHON", "马拉松选手", "单次专注达到 2 小时", false, null),
                new BadgeInfo("NIGHT_OWL", "夜猫子", "在 22 点后完成一次专注", false, null)));

        for (BadgeInfo b : badges) {
            if (earned.containsKey(b.code())) {
                badges.set(badges.indexOf(b), new BadgeInfo(b.code(), b.name(), b.description(), true,
                        earned.get(b.code())));
            }
        }
        return badges.stream().sorted(Comparator.comparing(BadgeInfo::earned).reversed()).toList();
    }

    private List<TitleInfo> evaluateTitles(UserStats stats) {
        int level = (int) (stats.getXp() / XP_PER_LEVEL) + 1;
        return List.of(
                new TitleInfo("专注学徒", "达到 1 级", level >= 1),
                new TitleInfo("专注行者", "达到 3 级", level >= 3),
                new TitleInfo("专注大师", "达到 5 级", level >= 5),
                new TitleInfo("自律之星", "连续打卡 7 天", stats.getBestStreak() >= 7),
                new TitleInfo("自律传说", "连续打卡 30 天", stats.getBestStreak() >= 30),
                new TitleInfo("千里之行", "累计专注 1000 分钟", stats.getDistinctDays() >= 0
                        && studySessionRepository.totalDurationSecondsByUserId(stats.getUser().getId()) / 60 >= 1000));
    }
}
