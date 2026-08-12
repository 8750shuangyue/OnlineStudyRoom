package com.studyroom.ai;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiMemoryRepository extends JpaRepository<AiMemory, Long> {

    List<AiMemory> findByUserIdAndSessionKeyOrderByCreatedAtDesc(
            Long userId, String sessionKey, Pageable pageable);

    void deleteByUserIdAndSessionKey(Long userId, String sessionKey);
}
