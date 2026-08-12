package com.studyroom.gamification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserGoalRepository extends JpaRepository<UserGoal, Long> {

    Optional<UserGoal> findByUserId(Long userId);
}
