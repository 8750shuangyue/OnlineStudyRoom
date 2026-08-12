package com.studyroom.realtime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long id, Pageable pageable);

    Optional<ChatMessage> findTopByRoomIdOrderByIdDesc(Long roomId);

    boolean existsByRoomIdAndIdLessThan(Long roomId, Long id);

    long countByRoomIdAndIdGreaterThan(Long roomId, Long id);

    @Query("""
            select m.id, count(r.id)
            from ChatMessage m
            left join ChatReadMark r on r.roomId = m.roomId and r.lastReadMessageId >= m.id
            where m.roomId = :roomId and m.id in :ids
            group by m.id
            """)
    List<Object[]> readCounts(@Param("roomId") Long roomId, @Param("ids") Collection<Long> ids);
}
