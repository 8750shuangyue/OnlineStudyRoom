package com.studyroom.mistake;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MistakeRequest(
        @Size(max = 50, message = "科目不能超过 50 字")
        String subject,

        @NotBlank(message = "题目不能为空")
        @Size(max = 2000, message = "题目不能超过 2000 字")
        String question,

        @Size(max = 2000, message = "笔记不能超过 2000 字")
        String note) {
}
