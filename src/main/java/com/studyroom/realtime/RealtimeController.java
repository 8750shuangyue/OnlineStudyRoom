package com.studyroom.realtime;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;

@Controller
public class RealtimeController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RealtimeController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 客户端发送：STOMP 目的地 /app/rooms/{roomId}/send，内容为 JSON 字符串。
     * 只有房间成员的消息会被持久化并广播；@提及会额外推送给被提及者。
     */
    @MessageMapping("/rooms/{roomId}/send")
    public void send(@DestinationVariable Long roomId, String payload, Principal principal) {
        try {
            MessageRequest request = objectMapper.readValue(payload, MessageRequest.class);
            chatService.send(roomId, principal.getName(), request.content());
        } catch (Exception ignored) {
            // 消息格式错误时忽略，不影响连接
        }
    }
}
