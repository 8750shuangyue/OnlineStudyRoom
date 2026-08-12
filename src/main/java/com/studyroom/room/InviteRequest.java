package com.studyroom.room;

import jakarta.validation.constraints.NotBlank;

public record InviteRequest(
        @NotBlank(message = "请指定用户名")
        String username) {
}
