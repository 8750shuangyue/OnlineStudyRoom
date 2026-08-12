package com.studyroom.ai;

import jakarta.validation.constraints.NotBlank;

public record ClearMemoryRequest(
        @NotBlank(message = "请指定会话类型")
        String sessionKey) {
}
