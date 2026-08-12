package com.studyroom.friend;

import java.time.LocalDateTime;

public record FriendRequestResponse(Long id, String username, LocalDateTime createdAt) {
}
