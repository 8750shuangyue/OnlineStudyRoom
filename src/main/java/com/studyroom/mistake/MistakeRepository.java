package com.studyroom.mistake;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MistakeRepository extends JpaRepository<Mistake, Long> {

    List<Mistake> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<Mistake> findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            Long userId, LocalDateTime now);

    long countByUserIdAndNextReviewAtLessThanEqual(Long userId, LocalDateTime now);

    long countByUserIdAndLastReviewedAtGreaterThanEqual(Long userId, LocalDateTime from);
}
