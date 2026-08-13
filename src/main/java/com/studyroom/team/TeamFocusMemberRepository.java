package com.studyroom.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamFocusMemberRepository extends JpaRepository<TeamFocusMember, Long> {

    List<TeamFocusMember> findByTeamFocusIdOrderByJoinedAtAsc(Long teamFocusId);

    Optional<TeamFocusMember> findByTeamFocusIdAndUserId(Long teamFocusId, Long userId);

    boolean existsByTeamFocusIdAndUserId(Long teamFocusId, Long userId);

    long countByTeamFocusId(Long teamFocusId);

    @Query("""
            select count(m) from TeamFocusMember m
            where m.user.id = :userId and m.teamFocus.status = 'FINISHED' and m.durationSeconds >= 900
            """)
    long countCompletedByUserId(@Param("userId") Long userId);

    @Query("""
            select count(m) from TeamFocusMember m
            where m.user.id = :userId and m.teamFocus.status = 'ACTIVE'
            """)
    long countActiveByUserId(@Param("userId") Long userId);
}
