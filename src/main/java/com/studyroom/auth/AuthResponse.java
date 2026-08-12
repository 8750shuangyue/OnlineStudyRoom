package com.studyroom.auth;

public record AuthResponse(String token, String refreshToken, String username) {
}
