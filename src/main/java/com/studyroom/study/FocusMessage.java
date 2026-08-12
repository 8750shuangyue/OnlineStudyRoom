package com.studyroom.study;

import java.time.LocalDateTime;

/**
 * 广播到 /topic/rooms/{roomId}/focus 的专注状态事件。
 */
public record FocusMessage(String type, String username, Long sessionId, LocalDateTime startedAt) {
}
