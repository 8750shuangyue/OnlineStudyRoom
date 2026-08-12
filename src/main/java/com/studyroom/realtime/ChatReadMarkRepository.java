package com.studyroom.realtime;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatReadMarkRepository extends JpaRepository<ChatReadMark, Long> {

    Optional<ChatReadMark> findByUserIdAndRoomId(Long userId, Long roomId);

    List<ChatReadMark> findByUserId(Long userId);

    void deleteByUserIdAndRoomId(Long userId, Long roomId);
}
