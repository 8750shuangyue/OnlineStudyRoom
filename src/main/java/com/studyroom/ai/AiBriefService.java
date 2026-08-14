package com.studyroom.ai;

import com.studyroom.flashcard.FlashcardRepository;
import com.studyroom.gamification.UserStats;
import com.studyroom.gamification.UserStatsRepository;
import com.studyroom.mistake.MistakeRepository;
import com.studyroom.notification.NotificationService;
import com.studyroom.study.StudySessionRepository;
import com.studyroom.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiBriefService {

    private static final Logger log = LoggerFactory.getLogger(AiBriefService.class);

    private final StudySessionRepository studySessionRepository;
    private final UserStatsRepository userStatsRepository;
    private final MistakeRepository mistakeRepository;
    private final FlashcardRepository flashcardRepository;
    private final NotificationService notificationService;
    private final AiService aiService;

    public AiBriefService(StudySessionRepository studySessionRepository,
                          UserStatsRepository userStatsRepository,
                          MistakeRepository mistakeRepository,
                          FlashcardRepository flashcardRepository,
                          NotificationService notificationService,
                          AiService aiService) {
        this.studySessionRepository = studySessionRepository;
        this.userStatsRepository = userStatsRepository;
        this.mistakeRepository = mistakeRepository;
        this.flashcardRepository = flashcardRepository;
        this.notificationService = notificationService;
        this.aiService = aiService;
    }

    /** 每天早上 7:30 给近 7 天有学习记录的用户生成简报。 */
    @Scheduled(cron = "0 30 7 * * *")
    public void scheduledDailyBriefs() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<Long> userIds = studySessionRepository.distinctUserIdsBetween(
                yesterday.atStartOfDay(), yesterday.plusDays(1).atStartOfDay());
        for (Long userId : userIds) {
            try {
                dailyBriefForUserId(userId);
            } catch (Exception e) {
                log.warn("生成每日简报失败 userId={}", userId, e);
            }
        }
    }

    @Transactional
    public String dailyBrief(User user) {
        return dailyBriefForUserId(user.getId());
    }

    private String dailyBriefForUserId(Long userId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime dayStart = yesterday.atStartOfDay();
        LocalDateTime dayEnd = yesterday.atTime(LocalTime.MAX);
        Map<LocalDate, Long> minutesByDay = secondsByDay(userId, dayStart, dayEnd);
        long minutes = minutesByDay.values().stream().mapToLong(Long::longValue).sum() / 60;
        long sessions = studySessionRepository.countByUserIdSince(userId, dayStart)
                - studySessionRepository.countByUserIdSince(userId, dayEnd.plusNanos(1));
        UserStats stats = userStatsRepository.findByUserId(userId).orElse(null);
        int streak = stats == null ? 0 : stats.getStreak();
        long dueMistakes = mistakeRepository.countByUserIdAndNextReviewAtLessThanEqual(
                userId, LocalDateTime.now());
        long dueCards = flashcardRepository.countByUserIdAndDueAtLessThanEqual(userId, LocalDateTime.now());

        String prompt = """
                你是学习助理。请为用户生成一份简短的每日学习简报（150 字以内，用 Markdown 无序列表），
                包含：昨日学习概况、今日建议（结合待复习事项）。

                昨日（%s）数据：
                - 专注 %d 次，共 %d 分钟
                - 连续打卡 %d 天
                - 今日到期：错题 %d 道，知识卡片 %d 张

                输出格式：
                **昨日回顾**：...
                **今日建议**：...
                """.formatted(yesterday, sessions, minutes, streak, dueMistakes, dueCards);
        String brief = aiService.askOnce(prompt);
        notificationService.create(userId, "DAILY_BRIEF", "每日学习简报",
                brief, null, "/messages");
        return brief;
    }

    @Transactional
    public String weeklyReport(User user) {
        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        LocalDateTime from = weekStart.atStartOfDay();
        Map<LocalDate, Long> minutesByDay = secondsByDay(user.getId(), from, LocalDateTime.now());
        long totalMinutes = minutesByDay.values().stream().mapToLong(Long::longValue).sum() / 60;
        long sessions = studySessionRepository.countByUserIdSince(user.getId(), from);
        long activeDays = minutesByDay.values().stream().filter(v -> v > 0).count();
        UserStats stats = userStatsRepository.findByUserId(user.getId()).orElse(null);
        int streak = stats == null ? 0 : stats.getStreak();
        long dueMistakes = mistakeRepository.countByUserIdAndNextReviewAtLessThanEqual(
                user.getId(), LocalDateTime.now());
        long dueCards = flashcardRepository.countByUserIdAndDueAtLessThanEqual(user.getId(), LocalDateTime.now());

        String prompt = """
                你是学习助理。请基于以下本周数据，生成一份 Markdown 格式的周度学习报告：
                包含：本周总览、亮点与问题、下周建议（至少 3 条）。

                本周（%s 起）数据：
                - 专注 %d 次，共 %d 分钟，活跃 %d 天
                - 当前连续打卡 %d 天
                - 待复习：错题 %d 道，知识卡片 %d 张
                """.formatted(weekStart, sessions, totalMinutes, activeDays, streak, dueMistakes, dueCards);
        String report = aiService.askOnce(prompt);
        notificationService.create(user.getId(), "WEEKLY_REPORT", "每周学习报告", report, null, "/messages");
        return report;
    }

    /** 每周日 20:00 给本周有学习记录的用户生成周报并推送站内信。 */
    @Scheduled(cron = "0 0 20 * * 0")
    public void scheduledWeeklyReports() {
        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        List<Long> userIds = studySessionRepository.distinctUserIdsSince(weekStart.atStartOfDay());
        for (Long userId : userIds) {
            try {
                weeklyReportForUserId(userId);
            } catch (Exception e) {
                log.warn("生成周度报告失败 userId={}", userId, e);
            }
        }
    }

    @Transactional
    public String weeklyReportForUserId(Long userId) {
        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        LocalDateTime from = weekStart.atStartOfDay();
        Map<LocalDate, Long> minutesByDay = secondsByDay(userId, from, LocalDateTime.now());
        long totalMinutes = minutesByDay.values().stream().mapToLong(Long::longValue).sum() / 60;
        long sessions = studySessionRepository.countByUserIdSince(userId, from);
        long activeDays = minutesByDay.values().stream().filter(v -> v > 0).count();
        UserStats stats = userStatsRepository.findByUserId(userId).orElse(null);
        int streak = stats == null ? 0 : stats.getStreak();
        long dueMistakes = mistakeRepository.countByUserIdAndNextReviewAtLessThanEqual(
                userId, LocalDateTime.now());
        long dueCards = flashcardRepository.countByUserIdAndDueAtLessThanEqual(userId, LocalDateTime.now());

        String prompt = """
                你是学习助理。请基于以下本周数据，生成一份 Markdown 格式的周度学习报告：
                包含：本周总览、亮点与问题、下周建议（至少 3 条）。
                本周（%s 起）数据：
                - 专注 %d 次，共 %d 分钟，活跃 %d 天
                - 当前连续打卡 %d 天
                - 待复习：错题 %d 道，知识卡片 %d 张
                """.formatted(weekStart, sessions, totalMinutes, activeDays, streak, dueMistakes, dueCards);
        String report = aiService.askOnce(prompt);
        notificationService.create(userId, "WEEKLY_REPORT", "每周学习报告", report, null, "/messages");
        return report;
    }

    private Map<LocalDate, Long> secondsByDay(Long userId, LocalDateTime from, LocalDateTime to) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : studySessionRepository.rawDurationSince(userId, from)) {
            LocalDateTime started = (LocalDateTime) row[0];
            if (started.isAfter(to)) {
                continue;
            }
            long seconds = (Long) row[1];
            byDay.merge(started.toLocalDate(), seconds, Long::sum);
        }
        return byDay;
    }
}
