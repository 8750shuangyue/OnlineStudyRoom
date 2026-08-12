package com.studyroom.room;

import com.studyroom.friend.FriendService;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class InviteController {

    private final RoomInviteRepository inviteRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final FriendService friendService;
    private final UserRepository userRepository;
    private final RoomService roomService;

    public InviteController(RoomInviteRepository inviteRepository,
                            RoomRepository roomRepository,
                            RoomMemberRepository roomMemberRepository,
                            FriendService friendService,
                            UserRepository userRepository,
                            RoomService roomService) {
        this.inviteRepository = inviteRepository;
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.friendService = friendService;
        this.userRepository = userRepository;
        this.roomService = roomService;
    }

    @PostMapping("/rooms/{roomId}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomInviteResponse invite(@PathVariable Long roomId,
                                     @Valid @RequestBody InviteRequest request,
                                     Authentication authentication) {
        User from = currentUser(authentication);
        Room room = roomRepository.findById(roomId)
                .filter(Room::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "房间不存在"));
        if (!roomMemberRepository.existsByRoomIdAndUserUsername(roomId, from.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有房间成员可以邀请");
        }
        if (!friendService.areFriends(from.getId(), request.username())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能邀请好友");
        }
        User to = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (roomMemberRepository.existsByRoomIdAndUserUsername(roomId, request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "对方已在该房间中");
        }
        RoomInvite invite = new RoomInvite();
        invite.setRoom(room);
        invite.setFrom(from);
        invite.setTo(to);
        invite.setCreatedAt(LocalDateTime.now());
        invite = inviteRepository.save(invite);
        return new RoomInviteResponse(invite.getId(), room.getId(), room.getName(),
                from.getUsername(), invite.getCreatedAt());
    }

    @GetMapping("/invites")
    @Transactional(readOnly = true)
    public List<RoomInviteResponse> myInvites(Authentication authentication) {
        User user = currentUser(authentication);
        return inviteRepository.findByToIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(i -> new RoomInviteResponse(i.getId(), i.getRoom().getId(),
                        i.getRoom().getName(), i.getFrom().getUsername(), i.getCreatedAt()))
                .toList();
    }

    @PostMapping("/invites/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        RoomInvite invite = inviteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "邀请不存在"));
        if (!invite.getTo().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能处理发给自己的邀请");
        }
        roomService.joinRoomByInvite(user, invite.getRoom().getId());
        inviteRepository.delete(invite);
    }

    @PostMapping("/invites/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        RoomInvite invite = inviteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "邀请不存在"));
        if (!invite.getTo().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能处理发给自己的邀请");
        }
        inviteRepository.delete(invite);
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }
}
