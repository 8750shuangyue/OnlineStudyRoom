package com.studyroom.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank(message = "任务内容不能为空")
        @Size(max = 200, message = "任务内容不能超过 200 字")
        String title) {
}
