package com.studyroom.gamification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BadgeRepository extends JpaRepository<Badge, Long> {

    List<Badge> findByUserId(Long userId);

    Optional<Badge> findByUserIdAndCode(Long userId, String code);
}
