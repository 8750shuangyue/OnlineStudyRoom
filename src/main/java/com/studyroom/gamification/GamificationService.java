package com.studyroom.gamification;

import com.studyroom.study.StudySession;
import com.studyroom.study.StudySessionRepository;
import com.studyroom.team.TeamFocusMemberRepository;
import com.studyroom.user.User;
import com.studyroom.notification.NotificationService;
import com.studyroom.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
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

    private static final Map<String, String> TEAM_BADGE_NAMES = Map.of(
            "TEAM_FIRST", "首战告捷",
            "TEAM_10", "默契十连",
            "TEAM_50", "铁血战队");

    private static final Map<String, String> SEASON_BADGE_NAMES = Map.of(
            "SEASON_60", "赛季新秀",
            "SEASON_300", "赛季主力",
            "SEASON_600", "赛季战神",
            "SEASON_5", "赛季勤勉",
            "SEASON_15", "赛季劳模",
            "SEASON_DAYS_4", "赛季常驻",
            "SEASON_CHAMPION", "赛季冠军");

    private static final Map<String, String> SEASON_BADGE_DESC = Map.of(
            "SEASON_60", "单赛季专注 60 分钟",
            "SEASON_300", "单赛季专注 300 分钟",
            "SEASON_600", "单赛季专注 600 分钟",
            "SEASON_5", "单赛季完成 5 次专注",
            "SEASON_15", "单赛季完成 15 次专注",
            "SEASON_DAYS_4", "单赛季 4 天有专注记录",
            "SEASON_CHAMPION", "单赛季在房间专注时长排名第一");

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
    private final SeasonAwardRepository seasonAwardRepository;
    private final TeamFocusMemberRepository teamFocusMemberRepository;
    private final NotificationService notificationService;
    private final RoomRepository roomRepository;

    public GamificationService(UserStatsRepository userStatsRepository,
                               BadgeRepository badgeRepository,
                               StudySessionRepository studySessionRepository,
                               ActivityService activityService,
                               SeasonAwardRepository seasonAwardRepository,
                               TeamFocusMemberRepository teamFocusMemberRepository,
                               NotificationService notificationService,
                               RoomRepository roomRepository) {
        this.userStatsRepository = userStatsRepository;
        this.badgeRepository = badgeRepository;
        this.studySessionRepository = studySessionRepository;
        this.activityService = activityService;
        this.seasonAwardRepository = seasonAwardRepository;
        this.teamFocusMemberRepository = teamFocusMemberRepository;
        this.notificationService = notificationService;
        this.roomRepository = roomRepository;
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
                evaluateTitles(stats),
                seasonAwards(user),
                currentSeason(user));
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
        return TEAM_BADGE_NAMES.getOrDefault(code, BADGE_NAMES.getOrDefault(code, code));
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

        badges.addAll(teamBadgeInfos());
        for (int i = 0; i < badges.size(); i++) {
            BadgeInfo b = badges.get(i);
            if (earned.containsKey(b.code())) {
                badges.set(i, new BadgeInfo(b.code(), b.name(), b.description(), true,
                        earned.get(b.code())));
            }
        }
        return badges.stream()
                .sorted(Comparator.comparing(BadgeInfo::earned).reversed()
                        .thenComparing(BadgeInfo::earnedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<TitleInfo> evaluateTitles(UserStats stats) {
        int level = (int) (stats.getXp() / XP_PER_LEVEL) + 1;
        return List.of(
                new TitleInfo("专注学徒", "达到 1 级", level >= 1),
                new TitleInfo("专注行者", "达到 3 级", level >= 3),
                new TitleInfo("专注大师", "达到 5 级", level >= 5),
                new TitleInfo("自律之星", "连续打卡 7 天", stats.getBestStreak() >= 7),
                new TitleInfo("自律传说", "连续打卡 30 天", stats.getBestStreak() >= 30),
                new TitleInfo("行者无疆", "累计专注 1000 分钟", studySessionRepository.totalDurationSecondsByUserId(stats.getUser().getId()) / 60 >= 1000));
    }

    private List<BadgeInfo> teamBadgeInfos() {
        return List.of(
                new BadgeInfo("TEAM_FIRST", "首战告捷", "完成第一次组队专注", false, null),
                new BadgeInfo("TEAM_10", "默契十连", "累计完成 10 次组队专注", false, null),
                new BadgeInfo("TEAM_50", "铁血战队", "累计完成 50 次组队专注", false, null));
    }

    /** 组队专注完成后调用：按累计完成次数发放团队徽章。 */
    @Transactional
    public void awardTeamFocusBadge(User user) {
        long completed = teamFocusMemberRepository.countCompletedByUserId(user.getId());
        awardSimpleBadge(user, "TEAM_FIRST", completed >= 1);
        awardSimpleBadge(user, "TEAM_10", completed >= 10);
        awardSimpleBadge(user, "TEAM_50", completed >= 50);
    }

    private void awardSimpleBadge(User user, String code, boolean met) {
        if (met && badgeRepository.findByUserIdAndCode(user.getId(), code).isEmpty()) {
            Badge badge = new Badge();
            badge.setUser(user);
            badge.setCode(code);
            badge.setEarnedAt(LocalDateTime.now());
            badgeRepository.save(badge);
            activityService.record(user.getId(), user.getUsername(), "BADGE_EARNED",
                    "获得新徽章「" + badgeName(code) + "」");
        }
    }

    /** 结算最近 4 个已结束的周赛季（周一开始、周日结束），幂等发放赛季徽章。 */
    @Transactional
    public void settleSeasons(User user) {
        LocalDate today = LocalDate.now();
        for (int back = 4; back >= 1; back--) {
            LocalDate monday = today.minusWeeks(back).with(DayOfWeek.MONDAY);
            settleSeason(user, monday);
        }
    }

    private void settleSeason(User user, LocalDate monday) {
        String key = seasonKey(monday);
        LocalDateTime from = monday.atStartOfDay();
        LocalDateTime to = from.plusWeeks(1);
        long minutes = studySessionRepository.totalDurationSecondsByUserIdBetween(user.getId(), from, to) / 60;
        long sessions = studySessionRepository.countByUserIdBetween(user.getId(), from, to);
        long days = studySessionRepository.startedAtBetween(user.getId(), from, to).stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .count();
        awardSeasonBadge(user, key, "SEASON_60", minutes >= 60, null);
        awardSeasonBadge(user, key, "SEASON_300", minutes >= 300, null);
        awardSeasonBadge(user, key, "SEASON_600", minutes >= 600, null);
        awardSeasonBadge(user, key, "SEASON_5", sessions >= 5, null);
        awardSeasonBadge(user, key, "SEASON_15", sessions >= 15, null);
        awardSeasonBadge(user, key, "SEASON_DAYS_4", days >= 4, null);
        awardSeasonChampion(user, key, from, to);
    }

    private void awardSeasonChampion(User user, String key, LocalDateTime from, LocalDateTime to) {
        for (Object[] row : studySessionRepository.seasonRoomMinutesByUserId(user.getId(), from, to)) {
            Long roomId = (Long) row[0];
            long seconds = ((Number) row[1]).longValue();
            if (seconds < 7200) {
                continue;
            }
            List<Object[]> board = studySessionRepository.seasonRoomLeaderboard(roomId, from, to);
            if (board.isEmpty() || !board.get(0)[0].equals(user.getId())) {
                continue;
            }
            String roomName = roomRepository.findById(roomId).map(r -> r.getName()).orElse(null);
            awardSeasonBadge(user, key, "SEASON_CHAMPION", true, roomName);
        }
    }

    private void awardSeasonBadge(User user, String key, String code, boolean met, String extra) {
        if (!met) {
            return;
        }
        if (seasonAwardRepository.findByUserIdAndCodeAndSeasonKey(user.getId(), code, key).isPresent()) {
            return;
        }
        SeasonAward award = new SeasonAward();
        award.setUser(user);
        award.setCode(code);
        award.setSeasonKey(key);
        award.setEarnedAt(LocalDateTime.now());
        award.setExtra(extra);
        seasonAwardRepository.save(award);
        String name = SEASON_BADGE_NAMES.getOrDefault(code, code);
        String suffix = extra == null || extra.isBlank() ? "" : "（" + extra + "）";
        activityService.record(user.getId(), user.getUsername(), "SEASON_BADGE",
                "赛季结算：获得「" + name + "」" + suffix);
        notificationService.create(user.getId(), "SEASON", "赛季徽章",
                "上个赛季你获得了「" + name + "」" + suffix, null, "/achievements");
    }

    private List<SeasonAwardInfo> seasonAwards(User user) {
        return seasonAwardRepository.findByUserIdOrderByEarnedAtDesc(user.getId()).stream()
                .map(a -> new SeasonAwardInfo(a.getCode(),
                        SEASON_BADGE_NAMES.getOrDefault(a.getCode(), a.getCode()),
                        SEASON_BADGE_DESC.getOrDefault(a.getCode(), ""),
                        a.getSeasonKey(), a.getEarnedAt(), a.getExtra()))
                .toList();
    }

    private SeasonProgress currentSeason(User user) {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDateTime from = monday.atStartOfDay();
        LocalDateTime to = from.plusWeeks(1);
        return new SeasonProgress(seasonKey(monday),
                studySessionRepository.totalDurationSecondsByUserIdBetween(user.getId(), from, to) / 60,
                studySessionRepository.countByUserIdBetween(user.getId(), from, to),
                studySessionRepository.startedAtBetween(user.getId(), from, to).stream()
                        .map(LocalDateTime::toLocalDate)
                        .distinct()
                        .count());
    }

    private String seasonKey(LocalDate date) {
        java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
        int week = date.get(weekFields.weekOfWeekBasedYear());
        int year = date.get(weekFields.weekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }
}
