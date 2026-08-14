package com.studyroom.dailytask;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyTaskRewardRepository extends JpaRepository<DailyTaskReward, Long> {

    List<DailyTaskReward> findByUserIdAndDate(Long userId, LocalDate date);
}
