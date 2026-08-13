package com.studyroom.gamification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeasonAwardRepository extends JpaRepository<SeasonAward, Long> {

    List<SeasonAward> findByUserIdOrderByEarnedAtDesc(Long userId);

    Optional<SeasonAward> findByUserIdAndCodeAndSeasonKey(Long userId, String code, String seasonKey);
}
