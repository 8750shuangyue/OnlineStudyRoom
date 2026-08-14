package com.studyroom.realtime;

import com.studyroom.friend.FriendRequestRepository;
import com.studyroom.push.WebPushService;
import com.studyroom.user.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 在 STOMP CONNECT 帧进入时登记在线人数。
 * 房间号优先从 CONNECT 头的 roomId 读取，其次读握手时 URL 参数写入的会话属性。
 */
@Component
public class StompPresenceChannelInterceptor implements ChannelInterceptor {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final WebPushService webPushService;

    public StompPresenceChannelInterceptor(PresenceService presenceService,
                                           @Lazy SimpMessagingTemplate messagingTemplate,
                                           UserRepository userRepository,
                                           FriendRequestRepository friendRequestRepository,
                                           WebPushService webPushService) {
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.webPushService = webPushService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        Long roomId = parseRoomId(accessor);
        String username = username(accessor);
        boolean firstOnline = username != null && !presenceService.isOnline(username);
        if (roomId != null && username != null) {
            presenceService.registerSession(accessor.getSessionId(), roomId, username);
            int count = presenceService.join(roomId, username);
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/presence",
                    new PresenceMessage(username, "JOIN", count));
        }
        if (firstOnline) {
            notifyFriendsOnline(username);
        }
        return message;
    }

    /** 用户从离线转为在线时，推送提醒其好友。 */
    private void notifyFriendsOnline(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            List<Long> friendIds = new ArrayList<>();
            friendIds.addAll(friendRequestRepository.findAcceptedFriendIdsWhereTo(user.getId()));
            friendIds.addAll(friendRequestRepository.findAcceptedFriendIdsWhereFrom(user.getId()));
            for (Long friendId : friendIds) {
                webPushService.sendToUser(friendId, "好友上线",
                        username + " 上线了，一起去自习室坐坐吧", "/rooms");
            }
        });
    }

    private Long parseRoomId(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("roomId");
        if (header != null) {
            try {
                return Long.parseLong(header);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get("roomId") instanceof Long roomId) {
            return roomId;
        }
        return null;
    }

    private String username(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null) {
            return accessor.getUser().getName();
        }
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return attrs == null ? null : (String) attrs.get("username");
    }
}
