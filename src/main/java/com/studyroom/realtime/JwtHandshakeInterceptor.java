package com.studyroom.realtime;

import com.studyroom.security.JwtService;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 握手时从 URL 参数读取 token 并校验，通过后把 username / roomId 写入会话属性。
 * 连接示例：ws://localhost:8081/ws?token=xxx&roomId=1
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtHandshakeInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = queryParam(request, "token");
        if (token == null) {
            return false;
        }
        try {
            String username = jwtService.extractUsername(token);
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || !jwtService.isTokenValid(token, user)) {
                return false;
            }
            attributes.put("username", username);
            String roomId = queryParam(request, "roomId");
            if (roomId != null) {
                try {
                    attributes.put("roomId", Long.parseLong(roomId));
                } catch (NumberFormatException ignored) {
                    // roomId 参数非法时忽略，仅不参与在线人数统计
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String queryParam(ServerHttpRequest request, String name) {
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && pair.substring(0, idx).equals(name)) {
                return URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
