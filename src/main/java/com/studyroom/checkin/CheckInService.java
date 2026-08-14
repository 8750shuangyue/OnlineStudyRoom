package com.studyroom.checkin;

import com.studyroom.activity.ActivityService;
import com.studyroom.gamification.GamificationService;
import com.studyroom.gamification.UserStats;
import com.studyroom.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 每日签到：连续签到天数（streak）、累计次数、每次 +10 XP。
 */
@Service
public class CheckInService {

    private static final int CHECKIN_XP = 10;

    private final CheckInRepository checkInRepository;
    private final GamificationService gamificationService;
    private final ActivityService activityService;

    public CheckInService(CheckInRepository checkInRepository,
                          GamificationService gamificationService,
                          ActivityService activityService) {
        this.checkInRepository = checkInRepository;
        this.gamificationService = gamificationService;
        this.activityService = activityService;
    }

    @Transactional(readOnly = true)
    public CheckInStatus status(User user) {
        LocalDate today = LocalDate.now();
        boolean checkedToday = checkInRepository.findByUserIdAndDate(user.getId(), today).isPresent();
        return new CheckInStatus(checkedToday, computeStreak(user.getId(), today),
                checkInRepository.countByUserId(user.getId()));
    }

    @Transactional
    public CheckInStatus checkIn(User user) {
        LocalDate today = LocalDate.now();
        if (checkInRepository.findByUserIdAndDate(user.getId(), today).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "今天已经签过到了");
        }
        CheckIn checkIn = new CheckIn();
        checkIn.setUser(user);
        checkIn.setDate(today);
        checkIn.setCreatedAt(LocalDateTime.now());
        checkInRepository.save(checkIn);

        int streak = computeStreak(user.getId(), today);
        UserStats stats = gamificationService.getOrCreateStats(user);
        stats.setXp(stats.getXp() + CHECKIN_XP);
        gamificationService.saveStats(stats);
        activityService.record(user.getId(), user.getUsername(), "CHECK_IN",
                "完成每日签到，连续 " + streak + " 天，+" + CHECKIN_XP + " XP");
        return new CheckInStatus(true, streak, checkInRepository.countByUserId(user.getId()));
    }

    private int computeStreak(Long userId, LocalDate today) {
        Set<LocalDate> dates = new HashSet<>();
        checkInRepository.findByUserIdOrderByDateDesc(userId)
                .forEach(c -> dates.add(c.getDate()));
        LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public record CheckInStatus(boolean checkedToday, int streak, long total) {
    }
}
