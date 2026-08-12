package com.studyroom.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentUpdateRequest(
        @NotBlank(message = "资料名称不能为空")
        @Size(max = 200, message = "名称不能超过 200 字")
        String name,

        @Size(max = 50, message = "分类不能超过 50 字")
        String category) {
}
