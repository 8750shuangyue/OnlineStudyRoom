package com.studyroom.gamification;

import com.studyroom.study.StudySessionRepository;
import com.studyroom.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class GoalService {

    private final UserGoalRepository userGoalRepository;
    private final StudySessionRepository studySessionRepository;

    public GoalService(UserGoalRepository userGoalRepository,
                       StudySessionRepository studySessionRepository) {
        this.userGoalRepository = userGoalRepository;
        this.studySessionRepository = studySessionRepository;
    }

    @Transactional
    public GoalResponse getGoal(User user) {
        return toResponse(user, getOrCreateGoal(user));
    }

    @Transactional
    public GoalResponse updateGoal(User user, int goalMinutes) {
        UserGoal goal = getOrCreateGoal(user);
        goal.setGoalMinutes(goalMinutes);
        userGoalRepository.save(goal);
        return toResponse(user, goal);
    }

    private UserGoal getOrCreateGoal(User user) {
        return userGoalRepository.findByUserId(user.getId()).orElseGet(() -> {
            UserGoal goal = new UserGoal();
            goal.setUser(user);
            goal.setGoalMinutes(120);
            return userGoalRepository.save(goal);
        });
    }

    private GoalResponse toResponse(User user, UserGoal goal) {
        long todayMinutes = studySessionRepository
                .totalDurationSecondsByUserIdSince(user.getId(), LocalDate.now().atStartOfDay()) / 60;
        int percent = (int) Math.min(100, todayMinutes * 100 / Math.max(1, goal.getGoalMinutes()));
        return new GoalResponse(goal.getGoalMinutes(), todayMinutes, percent);
    }
}
