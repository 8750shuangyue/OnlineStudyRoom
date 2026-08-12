package com.studyroom.stats;

import com.studyroom.room.RoomRepository;
import com.studyroom.study.StudySessionRepository;
import com.studyroom.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.studyroom.study.StudySession;

@Service
public class StatsService {

    private final StudySessionRepository studySessionRepository;
    private final RoomRepository roomRepository;

    public StatsService(StudySessionRepository studySessionRepository, RoomRepository roomRepository) {
        this.studySessionRepository = studySessionRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public StatsResponse myStats(User user) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return new StatsResponse(
                studySessionRepository.countByUserId(user.getId()),
                studySessionRepository.totalDurationSecondsByUserId(user.getId()),
                studySessionRepository.countByUserIdSince(user.getId(), todayStart),
                studySessionRepository.totalDurationSecondsByUserIdSince(user.getId(), todayStart));
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> roomLeaderboard(Long roomId, String period, String metric) {
        if (!roomRepository.existsById(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "房间不存在");
        }
        LocalDateTime from = rangeStart(period);
        return switch (metric == null ? "duration" : metric) {
            case "sessions" -> studySessionRepository.leaderboardSessionsByRoomId(roomId, from);
            case "streak" -> computeStreak(studySessionRepository.rawLeaderboardByRoomId(roomId, from));
            case "bestTime" -> computeBestTime(studySessionRepository.rawLeaderboardByRoomId(roomId, from));
            default -> studySessionRepository.leaderboardByRoomId(roomId, from);
        };
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> globalLeaderboard(String period, String metric) {
        LocalDateTime from = rangeStart(period);
        return switch (metric == null ? "duration" : metric) {
            case "sessions" -> studySessionRepository.globalLeaderboardSessions(from);
            case "streak" -> computeStreak(studySessionRepository.rawLeaderboard(from));
            case "bestTime" -> computeBestTime(studySessionRepository.rawLeaderboard(from));
            default -> studySessionRepository.globalLeaderboard(from);
        };
    }

    /** 连续打卡天数（允许今天还没开始学，从昨天起算）。 */
    private List<LeaderboardEntry> computeStreak(List<Object[]> raw) {
        Map<String, Set<LocalDate>> daysByUser = new HashMap<>();
        for (Object[] row : raw) {
            String username = (String) row[0];
            LocalDateTime started = (LocalDateTime) row[1];
            daysByUser.computeIfAbsent(username, k -> new HashSet<>()).add(started.toLocalDate());
        }
        return daysByUser.entrySet().stream()
                .map(entry -> new LeaderboardEntry(entry.getKey(),
                        (long) currentStreak(entry.getValue()), "天"))
                .filter(entry -> entry.value() > 0)
                .sorted(Comparator.comparing(LeaderboardEntry::value).reversed())
                .toList();
    }

    private int currentStreak(Set<LocalDate> days) {
        LocalDate cursor = LocalDate.now();
        if (!days.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 最佳时段：用户在哪个时段专注分钟最多。 */
    private List<LeaderboardEntry> computeBestTime(List<Object[]> raw) {
        Map<String, Map<String, Long>> minutesByUser = new HashMap<>();
        for (Object[] row : raw) {
            String username = (String) row[0];
            LocalDateTime started = (LocalDateTime) row[1];
            long seconds = (Long) row[2];
            String bucket = timeBucketName(started.getHour());
            minutesByUser.computeIfAbsent(username, k -> new HashMap<>())
                    .merge(bucket, Math.max(1, seconds / 60), Long::sum);
        }
        return minutesByUser.entrySet().stream()
                .map(entry -> {
                    Map.Entry<String, Long> best = entry.getValue().entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .orElseThrow();
                    return new LeaderboardEntry(entry.getKey(), best.getValue(),
                            best.getKey() + "时段·分钟");
                })
                .filter(entry -> entry.value() > 0)
                .sorted(Comparator.comparing(LeaderboardEntry::value).reversed())
                .toList();
    }

    private String timeBucketName(int hour) {
        if (hour < 6) return "深夜";
        if (hour < 12) return "上午";
        if (hour < 18) return "下午";
        return "晚上";
    }

    @Transactional(readOnly = true)
    public List<DailyStat> dailyTrend(User user, int days) {
        int count = Math.max(1, Math.min(days, 90));
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(count - 1L).atStartOfDay();
        Map<LocalDate, Long> byDate = secondsByDay(user.getId(), from);
        List<DailyStat> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate date = today.minusDays(count - 1L - i);
            result.add(new DailyStat(date, byDate.getOrDefault(date, 0L)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public WeeklyReport weekly(User user) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        Map<LocalDate, Long> byDate = secondsByDay(user.getId(), weekStart.atStartOfDay());
        long totalSeconds = byDate.values().stream().mapToLong(Long::longValue).sum();
        long sessions = studySessionRepository.countByUserIdSince(user.getId(), weekStart.atStartOfDay());
        long daysActive = byDate.size();
        Map.Entry<LocalDate, Long> best = byDate.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        return new WeeklyReport(totalSeconds / 60, sessions, daysActive,
                best == null ? null : best.getKey(),
                best == null ? 0 : best.getValue() / 60);
    }

    private Map<LocalDate, Long> secondsByDay(Long userId, LocalDateTime from) {
        Map<LocalDate, Long> byDate = new HashMap<>();
        for (Object[] row : studySessionRepository.rawDurationSince(userId, from)) {
            LocalDateTime started = (LocalDateTime) row[0];
            long seconds = (Long) row[1];
            byDate.merge(started.toLocalDate(), seconds, Long::sum);
        }
        return byDate;
    }

    @Transactional(readOnly = true)
    public List<TimeBucket> timeAnalysis(User user) {
        List<StudySession> sessions = studySessionRepository
                .findByUserIdAndDurationSecondsGreaterThanEqualAndStartedAtGreaterThanEqual(
                        user.getId(), 900, LocalDate.now().minusDays(89).atStartOfDay());
        long lateNight = 0;
        long morning = 0;
        long afternoon = 0;
        long evening = 0;
        for (StudySession s : sessions) {
            long minutes = Math.max(1, s.getDurationSeconds() / 60);
            int hour = s.getStartedAt().getHour();
            if (hour < 6) {
                lateNight += minutes;
            } else if (hour < 12) {
                morning += minutes;
            } else if (hour < 18) {
                afternoon += minutes;
            } else {
                evening += minutes;
            }
        }
        return List.of(
                new TimeBucket("深夜 (0-6 点)", lateNight),
                new TimeBucket("上午 (6-12 点)", morning),
                new TimeBucket("下午 (12-18 点)", afternoon),
                new TimeBucket("晚上 (18-24 点)", evening));
    }

    private LocalDateTime rangeStart(String period) {
        LocalDate today = LocalDate.now();
        return switch (period == null ? "all" : period) {
            case "today" -> today.atStartOfDay();
            case "week" -> today.with(DayOfWeek.MONDAY).atStartOfDay();
            case "month" -> today.withDayOfMonth(1).atStartOfDay();
            default -> null;
        };
    }
}
