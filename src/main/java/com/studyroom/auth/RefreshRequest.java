package com.studyroom.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken) {
}
