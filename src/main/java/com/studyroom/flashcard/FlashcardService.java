package com.studyroom.flashcard;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlashcardService {

    private static final int[] INTERVALS = {1, 3, 7, 14, 30, 60};

    private final FlashcardRepository flashcardRepository;

    public FlashcardService(FlashcardRepository flashcardRepository) {
        this.flashcardRepository = flashcardRepository;
    }

    @Transactional
    public FlashcardResponse create(Long userId, FlashcardRequest request) {
        Flashcard card = new Flashcard();
        card.setUserId(userId);
        card.setFront(request.front().trim());
        card.setBack(request.back().trim());
        card.setSourceType(request.sourceType());
        card.setSourceId(request.sourceId());
        card.setReviewCount(0);
        card.setDueAt(LocalDateTime.now());
        card.setCreatedAt(LocalDateTime.now());
        return FlashcardResponse.from(flashcardRepository.save(card));
    }

    @Transactional(readOnly = true)
    public List<FlashcardResponse> list(Long userId) {
        return flashcardRepository.findByUserIdOrderByDueAtAsc(userId).stream()
                .map(FlashcardResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long dueCount(Long userId) {
        return flashcardRepository.countByUserIdAndDueAtLessThanEqual(userId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<FlashcardResponse> dueCards(Long userId) {
        return flashcardRepository.findByUserIdAndDueAtLessThanEqualOrderByDueAtAsc(
                        userId, LocalDateTime.now()).stream()
                .map(FlashcardResponse::from)
                .toList();
    }

    /**
     * 复习并安排下次到期时间（间隔重复）。
     */
    @Transactional
    public FlashcardResponse review(Long userId, Long cardId, FlashcardRating rating) {
        Flashcard card = ownedCard(userId, cardId);
        LocalDateTime now = LocalDateTime.now();
        int nextCount = card.getReviewCount() + 1;
        int intervalDays;
        if (rating == FlashcardRating.HARD) {
            nextCount = Math.max(1, card.getReviewCount());
            intervalDays = 1;
        } else if (rating == FlashcardRating.EASY) {
            intervalDays = INTERVALS[Math.min(nextCount + 1, INTERVALS.length - 1)];
        } else {
            intervalDays = INTERVALS[Math.min(nextCount, INTERVALS.length - 1)];
        }
        card.setReviewCount(nextCount);
        card.setDueAt(now.plusDays(intervalDays));
        card.setLastReviewedAt(now);
        return FlashcardResponse.from(flashcardRepository.save(card));
    }

    @Transactional
    public void delete(Long userId, Long cardId) {
        flashcardRepository.delete(ownedCard(userId, cardId));
    }

    private Flashcard ownedCard(Long userId, Long cardId) {
        Flashcard card = flashcardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "卡片不存在"));
        if (!card.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作自己的卡片");
        }
        return card;
    }
}
