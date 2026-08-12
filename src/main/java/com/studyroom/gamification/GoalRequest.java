package com.studyroom.gamification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GoalRequest(
        @Min(value = 10, message = "目标至少 10 分钟")
        @Max(value = 1440, message = "目标最多 1440 分钟")
        int goalMinutes) {
}
