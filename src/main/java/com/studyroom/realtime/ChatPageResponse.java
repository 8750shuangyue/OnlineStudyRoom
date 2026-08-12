package com.studyroom.realtime;

import java.util.List;

public record ChatPageResponse(List<MessageResponse> messages, boolean hasMore) {
}
