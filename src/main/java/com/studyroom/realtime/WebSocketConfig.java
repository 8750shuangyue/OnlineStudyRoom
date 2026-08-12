package com.studyroom.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final JwtHandshakeHandler jwtHandshakeHandler;
    private final StompPresenceChannelInterceptor presenceChannelInterceptor;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           JwtHandshakeHandler jwtHandshakeHandler,
                           StompPresenceChannelInterceptor presenceChannelInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.jwtHandshakeHandler = jwtHandshakeHandler;
        this.presenceChannelInterceptor = presenceChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 广播主题：/topic/rooms/{roomId}/chat、/topic/rooms/{roomId}/presence
        registry.enableSimpleBroker("/topic");
        // 客户端发送目的地前缀：/app/rooms/{roomId}/send
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(presenceChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(jwtHandshakeHandler);
    }
}
