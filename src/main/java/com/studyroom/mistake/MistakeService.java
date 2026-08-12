package com.studyroom.mistake;

import com.studyroom.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MistakeService {

    /** 间隔重复间隔（天）：掌握后按 1/2/4/7/15/30 天递增 */
    private static final int[] INTERVALS = {1, 2, 4, 7, 15, 30};
    private static final int MASTER_THRESHOLD = 6;

    private final MistakeRepository mistakeRepository;

    public MistakeService(MistakeRepository mistakeRepository) {
        this.mistakeRepository = mistakeRepository;
    }

    @Transactional(readOnly = true)
    public List<MistakeResponse> list(User user) {
        return mistakeRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MistakeResponse create(User user, MistakeRequest request) {
        Mistake mistake = new Mistake();
        mistake.setUser(user);
        mistake.setSubject(trimToNull(request.subject()));
        mistake.setQuestion(request.question().trim());
        mistake.setNote(trimToNull(request.note()));
        mistake.setCreatedAt(LocalDateTime.now());
        mistake.setUpdatedAt(LocalDateTime.now());
        mistake.setReviewStatus(MistakeReviewStatus.NEW);
        mistake.setReviewCount(0);
        mistake.setNextReviewAt(LocalDateTime.now());
        return toResponse(mistakeRepository.save(mistake));
    }

    @Transactional
    public MistakeResponse update(User user, Long mistakeId, MistakeRequest request) {
        Mistake mistake = getOwnedMistake(user, mistakeId);
        mistake.setSubject(trimToNull(request.subject()));
        mistake.setQuestion(request.question().trim());
        mistake.setNote(trimToNull(request.note()));
        mistake.setUpdatedAt(LocalDateTime.now());
        return toResponse(mistakeRepository.save(mistake));
    }

    @Transactional
    public void delete(User user, Long mistakeId) {
        mistakeRepository.delete(getOwnedMistake(user, mistakeId));
    }

    @Transactional(readOnly = true)
    public List<MistakeResponse> listDue(User user) {
        return mistakeRepository
                .findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                        user.getId(), LocalDateTime.now())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long dueCount(User user) {
        return mistakeRepository.countByUserIdAndNextReviewAtLessThanEqual(
                user.getId(), LocalDateTime.now());
    }

    /**
     * 提交一次复习结果，按间隔重复算法安排下次复习。
     */
    @Transactional
    public MistakeResponse review(User user, Long mistakeId, boolean mastered) {
        Mistake mistake = getOwnedMistake(user, mistakeId);
        LocalDateTime now = LocalDateTime.now();
        if (mastered) {
            int nextCount = mistake.getReviewCount() + 1;
            mistake.setReviewCount(nextCount);
            if (nextCount >= MASTER_THRESHOLD) {
                mistake.setReviewStatus(MistakeReviewStatus.MASTERED);
            } else if (mistake.getReviewStatus() == MistakeReviewStatus.NEW) {
                mistake.setReviewStatus(MistakeReviewStatus.LEARNING);
            }
            int interval = INTERVALS[Math.min(nextCount - 1, INTERVALS.length - 1)];
            mistake.setNextReviewAt(now.plusDays(interval));
        } else {
            mistake.setReviewCount(Math.max(0, mistake.getReviewCount() - 1));
            mistake.setReviewStatus(MistakeReviewStatus.LEARNING);
            mistake.setNextReviewAt(now.plusDays(1));
        }
        mistake.setLastReviewedAt(now);
        mistake.setUpdatedAt(now);
        return toResponse(mistakeRepository.save(mistake));
    }

    public Mistake getOwnedMistake(User user, Long mistakeId) {
        Mistake mistake = mistakeRepository.findById(mistakeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "错题不存在"));
        if (!mistake.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作自己的错题");
        }
        return mistake;
    }

    private MistakeResponse toResponse(Mistake mistake) {
        return new MistakeResponse(mistake.getId(), mistake.getSubject(),
                mistake.getQuestion(), mistake.getNote(),
                mistake.getCreatedAt(), mistake.getUpdatedAt(),
                mistake.getReviewStatus(), mistake.getReviewCount(),
                mistake.getNextReviewAt(), mistake.getLastReviewedAt());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
