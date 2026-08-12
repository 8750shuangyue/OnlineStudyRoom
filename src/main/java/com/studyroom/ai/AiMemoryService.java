package com.studyroom.ai;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiMemoryService {

    /** 每个场景最多保留的行数（20 轮对话 = 40 行） */
    private static final int KEEP_ROWS = 40;

    private final AiMemoryRepository aiMemoryRepository;

    public AiMemoryService(AiMemoryRepository aiMemoryRepository) {
        this.aiMemoryRepository = aiMemoryRepository;
    }

    @Transactional
    public void add(Long userId, String sessionKey, String role, String content) {
        AiMemory memory = new AiMemory();
        memory.setUserId(userId);
        memory.setSessionKey(sessionKey);
        memory.setRole(role);
        memory.setContent(content);
        memory.setCreatedAt(LocalDateTime.now());
        aiMemoryRepository.save(memory);
        prune(userId, sessionKey);
    }

    @Transactional(readOnly = true)
    public List<AiMemory> recent(Long userId, String sessionKey, int limit) {
        List<AiMemory> rows = aiMemoryRepository.findByUserIdAndSessionKeyOrderByCreatedAtDesc(
                userId, sessionKey, PageRequest.of(0, limit));
        List<AiMemory> result = new ArrayList<>(rows);
        java.util.Collections.reverse(result);
        return result;
    }

    @Transactional
    public void clear(Long userId, String sessionKey) {
        aiMemoryRepository.deleteByUserIdAndSessionKey(userId, sessionKey);
    }

    private void prune(Long userId, String sessionKey) {
        List<AiMemory> overflow = aiMemoryRepository.findByUserIdAndSessionKeyOrderByCreatedAtDesc(
                userId, sessionKey, PageRequest.of(KEEP_ROWS, 500));
        if (!overflow.isEmpty()) {
            aiMemoryRepository.deleteAll(overflow);
        }
    }
}
