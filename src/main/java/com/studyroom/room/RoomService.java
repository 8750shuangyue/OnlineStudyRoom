package com.studyroom.room;

import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import com.studyroom.realtime.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;

    public RoomService(RoomRepository roomRepository,
                       RoomMemberRepository roomMemberRepository,
                       UserRepository userRepository,
                       ChatService chatService) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
    }

    @Transactional
    public RoomResponse createRoom(User owner, RoomRequest request) {
        Room room = new Room();
        room.setName(request.name().trim());
        room.setCategory(trimToNull(request.category()));
        room.setPassword(trimToNull(request.password()));
        room.setAnnouncement(trimToNull(request.announcement()));
        room.setFocusMinutes(normalizeMinutes(request.focusMinutes(), 180));
        room.setBreakMinutes(normalizeMinutes(request.breakMinutes(), 60));
        room.setOwner(owner);
        room.setCreatedAt(LocalDateTime.now());
        room = roomRepository.save(room);

        // 创建者自动成为房间成员
        RoomMember member = new RoomMember();
        member.setRoom(room);
        member.setUser(owner);
        member.setJoinedAt(LocalDateTime.now());
        roomMemberRepository.save(member);
        chatService.markReadOnJoin(room.getId(), owner.getId());

        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> listRooms(String search, String category) {
        return roomRepository.search(trimToNull(search), trimToNull(category)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listCategories() {
        return roomRepository.findDistinctCategories();
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> myRooms(User user) {
        return roomMemberRepository.findByUserId(user.getId()).stream()
                .map(RoomMember::getRoom)
                .filter(Room::isActive)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomDetailResponse getRoomDetail(Long roomId) {
        Room room = getRoomOrThrow(roomId);
        List<String> members = roomMemberRepository.findByRoomIdOrderByJoinedAtAsc(roomId).stream()
                .map(member -> member.getUser().getUsername())
                .toList();
        return new RoomDetailResponse(room.getId(), room.getName(), room.getCategory(),
                room.getAnnouncement(), room.getPassword() != null, room.getOwner().getUsername(),
                members.size(), room.getCreatedAt(), members,
                room.getFocusMinutes() == null ? 0 : room.getFocusMinutes(),
                room.getBreakMinutes() == null ? 0 : room.getBreakMinutes());
    }

    @Transactional
    public RoomResponse joinRoom(User user, Long roomId, String password) {
        Room room = getRoomOrThrow(roomId);
        if (room.getPassword() != null && !room.getPassword().equals(password)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "房间密码错误");
        }
        return joinAsMember(user, room, false);
    }

    @Transactional
    public RoomResponse joinRoomByInvite(User user, Long roomId) {
        Room room = getRoomOrThrow(roomId);
        return joinAsMember(user, room, true);
    }

    private RoomResponse joinAsMember(User user, Room room, boolean idempotent) {
        if (roomMemberRepository.existsByRoomIdAndUserId(room.getId(), user.getId())) {
            if (idempotent) {
                return toResponse(room);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "你已在该房间中");
        }
        RoomMember member = new RoomMember();
        member.setRoom(room);
        member.setUser(user);
        member.setJoinedAt(LocalDateTime.now());
        roomMemberRepository.save(member);
        chatService.markReadOnJoin(room.getId(), user.getId());
        return toResponse(room);
    }

    @Transactional
    public RoomResponse leaveRoom(User user, Long roomId) {
        Room room = getRoomOrThrow(roomId);
        RoomMember member = roomMemberRepository.findByRoomIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "你不在该房间中"));
        roomMemberRepository.delete(member);
        chatService.clearReadMark(roomId, user.getId());
        return toResponse(room);
    }

    @Transactional
    public RoomDetailResponse updateRoom(User owner, Long roomId, RoomRequest request) {
        Room room = getRoomOrThrow(roomId);
        requireOwner(owner, room);
        room.setName(request.name().trim());
        room.setCategory(trimToNull(request.category()));
        room.setAnnouncement(trimToNull(request.announcement()));
        room.setFocusMinutes(normalizeMinutes(request.focusMinutes(), 180));
        room.setBreakMinutes(normalizeMinutes(request.breakMinutes(), 60));
        if (request.password() != null) {
            // 空字符串 = 清除密码；非空 = 设置新密码
            room.setPassword(trimToNull(request.password()));
        }
        roomRepository.save(room);
        return getRoomDetail(roomId);
    }

    @Transactional
    public void deleteRoom(User owner, Long roomId) {
        Room room = getRoomOrThrow(roomId);
        requireOwner(owner, room);
        room.setActive(false);
        roomRepository.save(room);
    }

    @Transactional
    public RoomDetailResponse transferRoom(User owner, Long roomId, String newOwnerUsername) {
        Room room = getRoomOrThrow(roomId);
        requireOwner(owner, room);
        User newOwner = userRepository.findByUsername(newOwnerUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (!roomMemberRepository.existsByRoomIdAndUserUsername(roomId, newOwnerUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新房主必须是房间成员");
        }
        room.setOwner(newOwner);
        roomRepository.save(room);
        return getRoomDetail(roomId);
    }

    @Transactional
    public RoomDetailResponse kickMember(User owner, Long roomId, String username) {
        Room room = getRoomOrThrow(roomId);
        requireOwner(owner, room);
        if (room.getOwner().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能移除房主");
        }
        if (!roomMemberRepository.existsByRoomIdAndUserUsername(roomId, username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该用户不是房间成员");
        }
        User target = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        roomMemberRepository.deleteByRoomIdAndUserId(roomId, target.getId());
        chatService.clearReadMark(roomId, target.getId());
        return getRoomDetail(roomId);
    }

    private Room getRoomOrThrow(Long roomId) {
        return roomRepository.findById(roomId)
                .filter(Room::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "房间不存在"));
    }

    private void requireOwner(User user, Room room) {
        if (!room.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有房主可以执行此操作");
        }
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCategory(),
                room.getPassword() != null, room.getOwner().getUsername(),
                roomMemberRepository.countByRoomId(room.getId()), room.getCreatedAt());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int normalizeMinutes(Integer value, int max) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(value, max));
    }
}
