package com.studyroom.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudyPlanRequest(
        @NotBlank(message = "请填写学习目标")
        @Size(max = 200, message = "目标不能超过 200 字")
        String goal,

        @Min(value = 1, message = "每天至少 1 小时")
        @Max(value = 16, message = "每天最多 16 小时")
        Integer hoursPerDay) {
}
