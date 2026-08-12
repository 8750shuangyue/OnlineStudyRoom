package com.studyroom.realtime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long id, Pageable pageable);

    Optional<ChatMessage> findTopByRoomIdOrderByIdDesc(Long roomId);

    boolean existsByRoomIdAndIdLessThan(Long roomId, Long id);

    long countByRoomIdAndIdGreaterThan(Long roomId, Long id);
}
