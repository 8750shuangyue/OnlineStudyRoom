package com.studyroom.study;

import jakarta.validation.constraints.Size;

public record ReflectionRequest(
        @Size(max = 2000, message = "心得不能超过 2000 字")
        String text) {
}
