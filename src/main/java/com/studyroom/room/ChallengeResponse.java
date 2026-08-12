package com.studyroom.room;

public record ChallengeResponse(
        int goalMinutes,
        int totalMinutes,
        int progressPercent,
        boolean achieved) {
}
