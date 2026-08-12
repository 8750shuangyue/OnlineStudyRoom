package com.studyroom.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomInviteRepository extends JpaRepository<RoomInvite, Long> {

    List<RoomInvite> findByToIdOrderByCreatedAtDesc(Long toId);
}
