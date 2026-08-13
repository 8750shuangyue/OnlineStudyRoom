package com.studyroom.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamFocusRepository extends JpaRepository<TeamFocus, Long> {

    Optional<TeamFocus> findFirstByRoomIdAndStatusOrderByStartedAtDesc(Long roomId, TeamFocusStatus status);

    List<TeamFocus> findTop5ByRoomIdAndStatusOrderByEndedAtDesc(Long roomId, TeamFocusStatus status);

    boolean existsByRoomIdAndStatus(Long roomId, TeamFocusStatus status);
}
