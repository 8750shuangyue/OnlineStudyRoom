package com.studyroom.flashcard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FlashcardRequest(
        @NotBlank(message = "卡片正面不能为空")
        @Size(max = 1000, message = "正面不能超过 1000 字")
        String front,

        @NotBlank(message = "卡片背面不能为空")
        @Size(max = 2000, message = "背面不能超过 2000 字")
        String back,

        @Size(max = 20, message = "来源类型过长")
        String sourceType,

        Long sourceId) {
}
