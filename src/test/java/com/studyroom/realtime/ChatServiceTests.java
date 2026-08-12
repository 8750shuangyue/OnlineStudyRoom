package com.studyroom.realtime;

import com.studyroom.room.Room;
import com.studyroom.room.RoomMember;
import com.studyroom.room.RoomMemberRepository;
import com.studyroom.room.RoomRepository;
import com.studyroom.notification.NotificationService;
import com.studyroom.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTests {

    private ChatMessageRepository messageRepository;
    private ChatReadMarkRepository readMarkRepository;
    private RoomMemberRepository roomMemberRepository;
    private RoomRepository roomRepository;
    private SimpMessagingTemplate messagingTemplate;
    private NotificationService notificationService;
    private ChatService service;

    @BeforeEach
    void setUp() {
        messageRepository = mock(ChatMessageRepository.class);
        readMarkRepository = mock(ChatReadMarkRepository.class);
        roomMemberRepository = mock(RoomMemberRepository.class);
        roomRepository = mock(RoomRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        notificationService = mock(NotificationService.class);
        service = new ChatService(messageRepository, readMarkRepository,
                roomMemberRepository, roomRepository, messagingTemplate, notificationService);
    }

    private RoomMember member(String username) {
        User user = new User();
        user.setUsername(username);
        RoomMember member = new RoomMember();
        member.setUser(user);
        return member;
    }

    @Test
    void sendsMessageAndBroadcasts() {
        when(roomMemberRepository.existsByRoomIdAndUserUsername(1L, "alice")).thenReturn(true);
        when(roomMemberRepository.findByRoomIdOrderByJoinedAtAsc(1L)).thenReturn(List.of(member("alice")));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessage saved = service.send(1L, "alice", "大家好");

        assertThat(saved).isNotNull();
        assertThat(saved.getContent()).isEqualTo("大家好");
        verify(messageRepository).save(saved);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/1/chat"), any(MessageResponse.class));
    }

    @Test
    void nonMemberOrBlankContentIsIgnored() {
        when(roomMemberRepository.existsByRoomIdAndUserUsername(1L, "bob")).thenReturn(false);
        assertThat(service.send(1L, "bob", "hi")).isNull();
        verify(messageRepository, never()).save(any());

        when(roomMemberRepository.existsByRoomIdAndUserUsername(1L, "alice")).thenReturn(true);
        assertThat(service.send(1L, "alice", "   ")).isNull();
        verify(messageRepository, never()).save(any());
    }

    @Test
    void mentionTriggersNotificationToMentionedMember() {
        when(roomMemberRepository.existsByRoomIdAndUserUsername(1L, "alice")).thenReturn(true);
        when(roomMemberRepository.findByRoomIdOrderByJoinedAtAsc(1L))
                .thenReturn(List.of(member("alice"), member("bob")));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        Room room = new Room();
        room.setName("测试房间");
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        service.send(1L, "alice", "你好 @bob 一起加油");

        verify(messagingTemplate).convertAndSend(eq("/topic/mentions/bob"), any(MentionNotification.class));
        verify(messagingTemplate, never())
                .convertAndSend(eq("/topic/mentions/alice"), any(MentionNotification.class));
        verify(notificationService).createForUsername(eq("bob"), eq("MENTION"),
                anyString(), eq("你好 @bob 一起加油"), eq(1L), eq("/rooms/1"));
        verify(notificationService, never())
                .createForUsername(eq("alice"), anyString(), anyString(), anyString(), any(), any());

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getMentions()).containsExactly("bob");
    }

    @Test
    void mentionBoundaryRules() {
        assertThat(ChatService.matchesMention("bob", "你好 @bob 加油")).isTrue();
        assertThat(ChatService.matchesMention("bob", "@bob加油")).isTrue();
        assertThat(ChatService.matchesMention("小明", "记得叫 @小明 一起来")).isTrue();
        assertThat(ChatService.matchesMention("小明", "小明今天很棒")).isFalse();
        assertThat(ChatService.matchesMention("bob", "@bobby 是你吗")).isFalse();
        assertThat(ChatService.matchesMention("bob", "email@bob.com 里的内容")).isFalse();
    }
}
