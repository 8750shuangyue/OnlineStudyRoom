package com.studyroom.room;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.realtime.ChatPageResponse;
import com.studyroom.realtime.ChatService;
import com.studyroom.realtime.PresenceService;
import com.studyroom.realtime.UnreadResponse;
import com.studyroom.ai.AiService;
import com.studyroom.stats.StatsService;
import com.studyroom.study.FocusEntry;
import com.studyroom.study.StudyService;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 房间 REST 统一入口：房间增删改查/成员管理、在线状态、历史消息分页、未读数。
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController extends CurrentUserSupport {

    public record JoinRequest(String password) {
    }

    public record KickRequest(
            @NotBlank(message = "请指定要移除的成员")
            String username) {
    }

    public record TransferRequest(
            @NotBlank(message = "请指定新房主")
            String username) {
    }

    public record MuteRequest(
            @Min(value = 0, message = "禁言时长不能为负数")
            @Max(value = 1440, message = "禁言最长 24 小时")
            int minutes) {
    }

    public record TutorRequest(
            @NotBlank(message = "消息不能为空")
            String message) {
    }

    private final RoomService roomService;
    private final PresenceService presenceService;
    private final ChatService chatService;
    private final StudyService studyService;
    private final AiService aiService;
    private final StatsService statsService;

    public RoomController(RoomService roomService,
                          UserRepository userRepository,
                          PresenceService presenceService,
                          ChatService chatService,
                          StudyService studyService,
                          AiService aiService,
                          StatsService statsService) {
        super(userRepository);
        this.roomService = roomService;
        this.presenceService = presenceService;
        this.chatService = chatService;
        this.studyService = studyService;
        this.aiService = aiService;
        this.statsService = statsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@Valid @RequestBody RoomRequest request, Authentication authentication) {
        return roomService.createRoom(currentUser(authentication), request);
    }

    @GetMapping
    public List<RoomResponse> list(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) String category) {
        return roomService.listRooms(search, category);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return roomService.listCategories();
    }

    @GetMapping("/mine")
    public List<RoomResponse> myRooms(Authentication authentication) {
        return roomService.myRooms(currentUser(authentication));
    }

    @GetMapping("/unread")
    @Transactional(readOnly = true)
    public List<UnreadResponse> unread(Authentication authentication) {
        return chatService.unreadCounts(currentUser(authentication).getId());
    }

    /** 为你推荐：公开且未加入的房间。 */
    @GetMapping("/recommended")
    public List<RoomResponse> recommended(Authentication authentication) {
        return roomService.recommendedRooms(currentUser(authentication));
    }

    @GetMapping("/{id}")
    public RoomDetailResponse detail(@PathVariable Long id) {
        return roomService.getRoomDetail(id);
    }

    @GetMapping("/{roomId}/online")
    public Map<String, Object> online(@PathVariable Long roomId) {
        return Map.of(
                "onlineCount", presenceService.onlineCount(roomId),
                "usernames", presenceService.onlineUsers(roomId));
    }

    /** 历史消息分页：默认返回最新一页（时间正序），before=上页最早消息 id 时加载更早。 */
    @GetMapping("/{roomId}/messages")
    public ChatPageResponse messages(@PathVariable Long roomId,
                                     @RequestParam(required = false) Long before,
                                     @RequestParam(defaultValue = "50") int limit,
                                     Authentication authentication) {
        requireMember(roomId, authentication);
        return chatService.history(roomId, before, limit);
    }

    /** 标记房间已读（进入房间查看时调用）。 */
    @PostMapping("/{roomId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long roomId, Authentication authentication) {
        requireMember(roomId, authentication);
        chatService.markRead(roomId, currentUser(authentication).getId());
    }

    /** 房间内正在专注的成员。 */
    @GetMapping("/{roomId}/focus-status")
    @Transactional(readOnly = true)
    public List<FocusEntry> focusStatus(@PathVariable Long roomId) {
        return studyService.roomFocusStatus(roomId);
    }

    /** 本周房间挑战进度。 */
    @GetMapping("/{id}/challenge")
    public ChallengeResponse challenge(@PathVariable Long id) {
        RoomDetailResponse room = roomService.getRoomDetail(id);
        int goal = room.weeklyGoalMinutes();
        int total = 0;
        if (goal > 0) {
            total = statsService.roomLeaderboard(id, "week", "duration").stream()
                    .mapToInt(e -> e.value() == null ? 0 : e.value().intValue())
                    .sum();
        }
        int progress = goal > 0 ? Math.min(100, (int) Math.round(total * 100.0 / goal)) : 0;
        return new ChallengeResponse(goal, total, progress, goal > 0 && total >= goal);
    }

    @PostMapping("/{id}/join")
    public RoomResponse join(@PathVariable Long id,
                             @RequestBody(required = false) JoinRequest request,
                             Authentication authentication) {
        String password = request == null ? null : request.password();
        return roomService.joinRoom(currentUser(authentication), id, password);
    }

    @PostMapping("/{id}/leave")
    public RoomResponse leave(@PathVariable Long id, Authentication authentication) {
        return roomService.leaveRoom(currentUser(authentication), id);
    }

    @PutMapping("/{id}")
    public RoomDetailResponse update(@PathVariable Long id,
                                     @Valid @RequestBody RoomRequest request,
                                     Authentication authentication) {
        return roomService.updateRoom(currentUser(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        roomService.deleteRoom(currentUser(authentication), id);
    }

    @PostMapping("/{id}/transfer")
    public RoomDetailResponse transfer(@PathVariable Long id,
                                       @Valid @RequestBody TransferRequest request,
                                       Authentication authentication) {
        return roomService.transferRoom(currentUser(authentication), id, request.username().trim());
    }

    @PostMapping("/{id}/kick")
    public RoomDetailResponse kick(@PathVariable Long id,
                                   @Valid @RequestBody KickRequest request,
                                   Authentication authentication) {
        return roomService.kickMember(currentUser(authentication), id, request.username().trim());
    }

    @PostMapping("/{id}/members/{username}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mute(@PathVariable Long id,
                     @PathVariable String username,
                     @Valid @RequestBody MuteRequest request,
                     Authentication authentication) {
        roomService.muteMember(currentUser(authentication), id, username, request.minutes());
    }

    /** AI 房间助教：仅房间成员可用，且房主已开启。 */
    @PostMapping("/{id}/tutor")
    public java.util.Map<String, String> tutor(@PathVariable Long id,
                                               @Valid @RequestBody TutorRequest request,
                                               Authentication authentication) {
        User user = currentUser(authentication);
        if (!chatService.isMember(id, user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有房间成员可以提问助教");
        }
        RoomDetailResponse room = roomService.getRoomDetail(id);
        if (!room.aiTutorEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该房间未开启 AI 助教");
        }
        String reply = aiService.tutorAnswer(user, id, room.tutorPersona(), room.name(),
                room.announcement(), String.join("、", room.members()), request.message());
        return java.util.Map.of("reply", reply);
    }

    private void requireMember(Long roomId, Authentication authentication) {
        if (!chatService.isMember(roomId, authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有房间成员可以查看消息");
        }
    }

}
