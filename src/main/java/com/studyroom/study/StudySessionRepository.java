package com.studyroom.study;

import com.studyroom.stats.LeaderboardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    Optional<StudySession> findByUserIdAndEndedAtIsNull(Long userId);

    List<StudySession> findByUserIdOrderByStartedAtDesc(Long userId);

    List<StudySession> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    List<StudySession> findByRoomIdAndEndedAtIsNull(Long roomId);

    @Query("""
            select coalesce(sum(s.durationSeconds), 0) from StudySession s
            where s.user.id = :userId and s.durationSeconds >= 900
            """)
    long totalDurationSecondsByUserId(@Param("userId") Long userId);

    @Query("""
            select count(s) from StudySession s
            where s.user.id = :userId and s.durationSeconds >= 900
            """)
    long countByUserId(@Param("userId") Long userId);

    @Query("""
            select count(s) from StudySession s
            where s.user.id = :userId and s.startedAt >= :from and s.durationSeconds >= 900
            """)
    long countByUserIdSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Query("""
            select coalesce(sum(s.durationSeconds), 0) from StudySession s
            where s.user.id = :userId and s.startedAt >= :from and s.durationSeconds >= 900
            """)
    long totalDurationSecondsByUserIdSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Query("""
            select new com.studyroom.stats.LeaderboardEntry(s.user.username, sum(s.durationSeconds) / 60, '分钟')
            from StudySession s
            where s.room.id = :roomId and s.durationSeconds >= 900
              and (:from is null or s.startedAt >= :from)
            group by s.user.id, s.user.username
            order by sum(s.durationSeconds) desc
            """)
    List<LeaderboardEntry> leaderboardByRoomId(@Param("roomId") Long roomId,
                                               @Param("from") LocalDateTime from);

    @Query("""
            select new com.studyroom.stats.LeaderboardEntry(s.user.username, sum(s.durationSeconds) / 60, '分钟')
            from StudySession s
            where s.durationSeconds >= 900 and (:from is null or s.startedAt >= :from)
            group by s.user.id, s.user.username
            order by sum(s.durationSeconds) desc
            """)
    List<LeaderboardEntry> globalLeaderboard(@Param("from") LocalDateTime from);

    @Query("""
            select new com.studyroom.stats.LeaderboardEntry(s.user.username, count(s), '次')
            from StudySession s
            where s.room.id = :roomId and s.durationSeconds >= 900
              and (:from is null or s.startedAt >= :from)
            group by s.user.id, s.user.username
            order by count(s) desc
            """)
    List<LeaderboardEntry> leaderboardSessionsByRoomId(@Param("roomId") Long roomId,
                                                       @Param("from") LocalDateTime from);

    @Query("""
            select new com.studyroom.stats.LeaderboardEntry(s.user.username, count(s), '次')
            from StudySession s
            where s.durationSeconds >= 900 and (:from is null or s.startedAt >= :from)
            group by s.user.id, s.user.username
            order by count(s) desc
            """)
    List<LeaderboardEntry> globalLeaderboardSessions(@Param("from") LocalDateTime from);

    @Query("""
            select s.user.username, s.startedAt, s.durationSeconds
            from StudySession s
            where s.durationSeconds >= 900 and (:from is null or s.startedAt >= :from)
            """)
    List<Object[]> rawLeaderboard(@Param("from") LocalDateTime from);

    @Query("""
            select s.user.username, s.startedAt, s.durationSeconds
            from StudySession s
            where s.room.id = :roomId and s.durationSeconds >= 900
              and (:from is null or s.startedAt >= :from)
            """)
    List<Object[]> rawLeaderboardByRoomId(@Param("roomId") Long roomId,
                                          @Param("from") LocalDateTime from);

    @Query("""
            select s.startedAt, s.durationSeconds
            from StudySession s
            where s.user.id = :userId and s.durationSeconds >= 900 and s.startedAt >= :from
            """)
    List<Object[]> rawDurationSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    List<StudySession> findByUserIdAndDurationSecondsGreaterThanEqualAndStartedAtGreaterThanEqual(
            Long userId, long minSeconds, LocalDateTime from);
}
