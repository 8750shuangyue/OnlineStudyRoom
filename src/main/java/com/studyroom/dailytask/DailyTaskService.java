package com.studyroom.dailytask;

import com.studyroom.checkin.CheckInRepository;
import com.studyroom.flashcard.FlashcardRepository;
import com.studyroom.gamification.GamificationService;
import com.studyroom.gamification.UserStats;
import com.studyroom.mistake.MistakeRepository;
import com.studyroom.study.StudySessionRepository;
import com.studyroom.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 每日任务：完成当日目标可领取 +10 XP（每天每个任务限领一次）。
 */
@Service
public class DailyTaskService {

    private static final int TASK_XP = 10;

    private final DailyTaskRewardRepository rewardRepository;
    private final StudySessionRepository studySessionRepository;
    private final MistakeRepository mistakeRepository;
    private final FlashcardRepository flashcardRepository;
    private final CheckInRepository checkInRepository;
    private final GamificationService gamificationService;

    public DailyTaskService(DailyTaskRewardRepository rewardRepository,
                            StudySessionRepository studySessionRepository,
                            MistakeRepository mistakeRepository,
                            FlashcardRepository flashcardRepository,
                            CheckInRepository checkInRepository,
                            GamificationService gamificationService) {
        this.rewardRepository = rewardRepository;
        this.studySessionRepository = studySessionRepository;
        this.mistakeRepository = mistakeRepository;
        this.flashcardRepository = flashcardRepository;
        this.checkInRepository = checkInRepository;
        this.gamificationService = gamificationService;
    }

    @Transactional(readOnly = true)
    public List<TaskView> status(User user) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Set<String> rewarded = rewardedKeys(user.getId());
        return List.of(
                task("focus", "完成 1 次专注", "单次专注 ≥ 15 分钟",
                        studySessionRepository.countByUserIdSince(user.getId(), todayStart), 1, rewarded),
                task("mistakes", "复习 5 道错题", "完成错题复习",
                        mistakeRepository.countByUserIdAndLastReviewedAtGreaterThanEqual(user.getId(), todayStart),
                        5, rewarded),
                task("cards", "复习 10 张知识卡片", "完成闪卡复习",
                        flashcardRepository.countByUserIdAndLastReviewedAtGreaterThanEqual(user.getId(), todayStart),
                        10, rewarded),
                task("checkin", "每日签到", "连续签到不停歇",
                        checkInRepository.findByUserIdAndDate(user.getId(), LocalDate.now()).isPresent() ? 1 : 0,
                        1, rewarded)
        );
    }

    @Transactional
    public TaskView claim(User user, String key) {
        List<TaskView> tasks = status(user);
        TaskView task = tasks.stream()
                .filter(t -> t.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.done()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务还没完成");
        }
        if (task.rewarded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "奖励已领取");
        }
        DailyTaskReward reward = new DailyTaskReward();
        reward.setUser(user);
        reward.setDate(LocalDate.now());
        reward.setTaskKey(key);
        reward.setCreatedAt(LocalDateTime.now());
        rewardRepository.save(reward);

        UserStats stats = gamificationService.getOrCreateStats(user);
        stats.setXp(stats.getXp() + TASK_XP);
        gamificationService.saveStats(stats);
        return new TaskView(task.key(), task.title(), task.desc(), task.progress(), task.target(),
                true, true);
    }

    private Set<String> rewardedKeys(Long userId) {
        Set<String> keys = new HashSet<>();
        rewardRepository.findByUserIdAndDate(userId, LocalDate.now())
                .forEach(r -> keys.add(r.getTaskKey()));
        return keys;
    }

    private TaskView task(String key, String title, String desc, long progress, long target,
                          Set<String> rewarded) {
        boolean done = progress >= target;
        return new TaskView(key, title, desc, progress, target, done, done && rewarded.contains(key));
    }

    public record TaskView(String key, String title, String desc, long progress, long target,
                           boolean done, boolean rewarded) {
    }
}
