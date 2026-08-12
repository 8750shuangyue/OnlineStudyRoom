package com.studyroom.task;

import jakarta.validation.constraints.Size;

public record TaskUpdateRequest(
        @Size(max = 200, message = "任务内容不能超过 200 字")
        String title,
        Boolean done) {
}
