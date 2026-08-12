package com.studyroom.realtime;

import com.studyroom.realtime.PresenceService.SessionInfo;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceEventListener(PresenceService presenceService,
                                 @Lazy SimpMessagingTemplate messagingTemplate) {
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        SessionInfo info = presenceService.removeSession(accessor.getSessionId());
        if (info == null) {
            return;
        }
        int count = presenceService.leave(info.roomId(), info.username());
        messagingTemplate.convertAndSend("/topic/rooms/" + info.roomId() + "/presence",
                new PresenceMessage(info.username(), "LEAVE", count));
    }
}
