package com.studyroom.study;

import java.time.LocalDateTime;

public record FocusEntry(String username, Long sessionId, LocalDateTime startedAt) {
}
