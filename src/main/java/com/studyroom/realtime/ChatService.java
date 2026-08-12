package com.studyroom.realtime;

import com.studyroom.room.Room;
import com.studyroom.room.RoomMemberRepository;
import com.studyroom.room.RoomRepository;
import com.studyroom.notification.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 房间聊天核心逻辑：发送持久化 + 广播 + @提及通知，未读标记，历史分页。
 */
@Service
public class ChatService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    private final ChatMessageRepository messageRepository;
    private final ChatReadMarkRepository readMarkRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomRepository roomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    public ChatService(ChatMessageRepository messageRepository,
                       ChatReadMarkRepository readMarkRepository,
                       RoomMemberRepository roomMemberRepository,
                       RoomRepository roomRepository,
                       SimpMessagingTemplate messagingTemplate,
                       NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.readMarkRepository = readMarkRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.roomRepository = roomRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
    }

    /**
     * 发送聊天消息：仅房间成员可发，空白内容忽略；保存后广播并推送 @提及通知。
     */
    @Transactional
    public ChatMessage send(Long roomId, String username, String content) {
        if (!roomMemberRepository.existsByRoomIdAndUserUsername(roomId, username)) {
            return null;
        }
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setUsername(username);
        message.setContent(trimmed);
        message.setMentions(extractMentions(roomId, trimmed));
        message.setCreatedAt(LocalDateTime.now());
        messageRepository.save(message);

        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/chat", MessageResponse.from(message));
        notifyMentions(roomId, username, message);
        return message;
    }

    /**
     * 解析内容中 @ 到的房间成员（精确匹配成员名，避免误伤普通文本）。
     */
    public List<String> extractMentions(Long roomId, String content) {
        Set<String> mentions = new LinkedHashSet<>();
        for (var member : roomMemberRepository.findByRoomIdOrderByJoinedAtAsc(roomId)) {
            String name = member.getUser().getUsername();
            if (matchesMention(name, content)) {
                mentions.add(name);
            }
        }
        return new ArrayList<>(mentions);
    }

    private void notifyMentions(Long roomId, String fromUsername, ChatMessage message) {
        if (message.getMentions().isEmpty()) {
            return;
        }
        String roomName = roomRepository.findById(roomId).map(Room::getName).orElse(null);
        MentionNotification notification = new MentionNotification(roomId, roomName,
                fromUsername, message.getContent(), message.getCreatedAt());
        for (String mention : message.getMentions()) {
            if (!mention.equals(fromUsername)) {
                messagingTemplate.convertAndSend("/topic/mentions/" + mention, notification);
                notificationService.createForUsername(mention, "MENTION",
                        fromUsername + " 在房间提到了你", message.getContent(),
                        roomId, "/rooms/" + roomId);
            }
        }
    }

    static boolean matchesMention(String username, String content) {
        String quoted = Pattern.quote(username);
        // 边界只拦 ASCII 单词字符，中文直接跟在 @用户名 后也视为提及
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_])@" + quoted + "(?![A-Za-z0-9_])");
        return pattern.matcher(content).find();
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long roomId, String username) {
        return roomMemberRepository.existsByRoomIdAndUserUsername(roomId, username);
    }

    @Transactional(readOnly = true)
    public ChatPageResponse history(Long roomId, Long before, int limit) {
        int pageSize = Math.max(1, Math.min(limit <= 0 ? DEFAULT_PAGE_SIZE : limit, MAX_PAGE_SIZE));
        List<ChatMessage> rows;
        if (before == null) {
            rows = messageRepository.findByRoomIdOrderByIdDesc(roomId, PageRequest.of(0, pageSize));
        } else {
            rows = messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, before,
                    PageRequest.of(0, pageSize));
        }
        Collections.reverse(rows);
        boolean hasMore = false;
        if (!rows.isEmpty()) {
            hasMore = messageRepository.existsByRoomIdAndIdLessThan(roomId, rows.get(0).getId());
        }
        List<MessageResponse> messages = rows.stream().map(MessageResponse::from).toList();
        java.util.Map<Long, Integer> readCounts = new java.util.HashMap<>();
        if (!rows.isEmpty()) {
            List<Long> ids = rows.stream().map(ChatMessage::getId).toList();
            for (Object[] row : messageRepository.readCounts(roomId, ids)) {
                readCounts.put((Long) row[0], ((Number) row[1]).intValue());
            }
        }
        messages = rows.stream()
                .map(m -> MessageResponse.from(m, readCounts.getOrDefault(m.getId(), 0)))
                .toList();
        return new ChatPageResponse(messages, hasMore);
    }

    /** 加入房间时把当前消息都视为已读。 */
    @Transactional
    public void markReadOnJoin(Long roomId, Long userId) {
        upsertReadMark(roomId, userId);
    }

    /** 用户进入房间查看时标记已读。 */
    @Transactional
    public void markRead(Long roomId, Long userId) {
        upsertReadMark(roomId, userId);
    }

    private void upsertReadMark(Long roomId, Long userId) {
        long lastId = messageRepository.findTopByRoomIdOrderByIdDesc(roomId)
                .map(ChatMessage::getId)
                .orElse(0L);
        ChatReadMark mark = readMarkRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseGet(() -> {
                    ChatReadMark created = new ChatReadMark();
                    created.setUserId(userId);
                    created.setRoomId(roomId);
                    return created;
                });
        mark.setLastReadMessageId(Math.max(mark.getLastReadMessageId() == null ? 0L : mark.getLastReadMessageId(), lastId));
        mark.setUpdatedAt(LocalDateTime.now());
        readMarkRepository.save(mark);
    }

    /** 退出/被移除房间时清除已读标记。 */
    @Transactional
    public void clearReadMark(Long roomId, Long userId) {
        readMarkRepository.deleteByUserIdAndRoomId(userId, roomId);
    }

    @Transactional(readOnly = true)
    public List<UnreadResponse> unreadCounts(Long userId) {
        List<UnreadResponse> result = new ArrayList<>();
        for (ChatReadMark mark : readMarkRepository.findByUserId(userId)) {
            if (!roomMemberRepository.existsByRoomIdAndUserId(mark.getRoomId(), userId)) {
                continue;
            }
            long unread = messageRepository.countByRoomIdAndIdGreaterThan(
                    mark.getRoomId(), mark.getLastReadMessageId() == null ? 0L : mark.getLastReadMessageId());
            if (unread > 0) {
                result.add(new UnreadResponse(mark.getRoomId(), (int) Math.min(unread, Integer.MAX_VALUE)));
            }
        }
        return result;
    }
}
