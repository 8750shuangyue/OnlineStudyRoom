package com.studyroom.realtime;

public record PresenceMessage(String username, String event, int onlineCount) {
}
