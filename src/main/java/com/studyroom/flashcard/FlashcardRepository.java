package com.studyroom.flashcard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findByUserIdOrderByDueAtAsc(Long userId);

    List<Flashcard> findByUserIdAndDueAtLessThanEqualOrderByDueAtAsc(Long userId, LocalDateTime now);

    long countByUserIdAndDueAtLessThanEqual(Long userId, LocalDateTime now);

    long countByUserIdAndLastReviewedAtGreaterThanEqual(Long userId, LocalDateTime from);
}
