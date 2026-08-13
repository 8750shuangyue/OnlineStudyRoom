package com.studyroom.team;

import java.time.LocalDateTime;
import java.util.List;

public record TeamFocusResponse(
        Long id,
        Long roomId,
        String status,
        LocalDateTime startedAt,
        int plannedMinutes,
        LocalDateTime endedAt,
        long remainingSeconds,
        List<Member> members) {

    public record Member(
            String username,
            LocalDateTime joinedAt,
            LocalDateTime endedAt,
            Long durationSeconds,
            boolean finished) {
    }
}
