package com.studyroom.study;

import java.time.LocalDateTime;

/**
 * 广播到 /topic/rooms/{roomId}/sync 的同步专注事件。
 */
public record SyncMessage(String type, String username, LocalDateTime startedAt) {
}
