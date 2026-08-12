package com.studyroom.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    boolean existsByRoomIdAndUserUsername(Long roomId, String username);

    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);

    List<RoomMember> findByRoomIdOrderByJoinedAtAsc(Long roomId);

    List<RoomMember> findByUserId(Long userId);

    void deleteByRoomIdAndUserId(Long roomId, Long userId);
}
