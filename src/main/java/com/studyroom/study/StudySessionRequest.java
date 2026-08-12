package com.studyroom.study;

import jakarta.validation.constraints.NotNull;

public record StudySessionRequest(
        @NotNull(message = "请指定房间")
        Long roomId,
        Long taskId) {
}
