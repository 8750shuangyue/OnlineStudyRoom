package com.studyroom.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    boolean existsByRoomIdAndUserUsername(Long roomId, String username);

    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    Optional<RoomMember> findByRoomIdAndUserUsername(Long roomId, String username);

    long countByRoomId(Long roomId);

    List<RoomMember> findByRoomIdOrderByJoinedAtAsc(Long roomId);

    List<RoomMember> findByUserId(Long userId);

    void deleteByRoomIdAndUserId(Long roomId, Long userId);

    @Query("""
            select m.room.id, count(m) from RoomMember m
            where m.room.id in :roomIds
            group by m.room.id
            """)
    List<Object[]> countByRoomIds(@Param("roomIds") Collection<Long> roomIds);
}
