package com.studyroom.ai;

import jakarta.validation.constraints.NotBlank;

public record RAGRequest(
        @NotBlank(message = "问题不能为空")
        String question) {
}
