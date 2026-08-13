package com.studyroom.team;

import com.studyroom.activity.ActivityService;
import com.studyroom.gamification.GamificationService;
import com.studyroom.notification.NotificationService;
import com.studyroom.room.Room;
import com.studyroom.room.RoomMemberRepository;
import com.studyroom.room.RoomRepository;
import com.studyroom.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TeamFocusService {

    private static final int MAX_MEMBERS = 6;
    private static final int MAX_PLANNED_MINUTES = 180;

    private final TeamFocusRepository teamFocusRepository;
    private final TeamFocusMemberRepository memberRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final GamificationService gamificationService;

    public TeamFocusService(TeamFocusRepository teamFocusRepository,
                            TeamFocusMemberRepository memberRepository,
                            RoomRepository roomRepository,
                            RoomMemberRepository roomMemberRepository,
                            SimpMessagingTemplate messagingTemplate,
                            ActivityService activityService,
                            NotificationService notificationService,
                            GamificationService gamificationService) {
        this.teamFocusRepository = teamFocusRepository;
        this.memberRepository = memberRepository;
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.messagingTemplate = messagingTemplate;
        this.activityService = activityService;
        this.notificationService = notificationService;
        this.gamificationService = gamificationService;
    }

    @Transactional
    public TeamFocusResponse start(User user, Long roomId, Integer plannedMinutes) {
        requireMember(roomId, user.getId());
        if (teamFocusRepository.existsByRoomIdAndStatus(roomId, TeamFocusStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "�÷����Ѿ��л�еĶ���רע");
        }
        if (memberRepository.countActiveByUserId(user.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "���Ѿ��������Ķ���רע��");
        }
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "���䲻����"));
        int minutes = plannedMinutes == null || plannedMinutes <= 0 ? 25
                : Math.min(plannedMinutes, MAX_PLANNED_MINUTES);

        TeamFocus focus = new TeamFocus();
        focus.setRoom(room);
        focus.setStartedBy(user);
        focus.setStartedAt(LocalDateTime.now());
        focus.setPlannedMinutes(minutes);
        focus.setStatus(TeamFocusStatus.ACTIVE);
        teamFocusRepository.save(focus);
        addMember(focus, user);
        broadcast(focus);
        return toResponse(focus);
    }

    @Transactional
    public TeamFocusResponse join(User user, Long roomId, Long teamFocusId) {
        requireMember(roomId, user.getId());
        TeamFocus focus = getActiveOrThrow(teamFocusId, roomId);
        if (memberRepository.existsByTeamFocusIdAndUserId(teamFocusId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "���Ѿ��ڸö�����");
        }
        if (memberRepository.countByTeamFocusId(teamFocusId) >= MAX_MEMBERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "����Ѿ����ˣ���� 6 �ˣ�");
        }
        if (memberRepository.countActiveByUserId(user.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "���Ѿ��������Ķ���רע��");
        }
        addMember(focus, user);
        broadcast(focus);
        return toResponse(focus);
    }

    @Transactional
    public TeamFocusResponse stop(User user, Long roomId, Long teamFocusId) {
        requireMember(roomId, user.getId());
        TeamFocus focus = getActiveOrThrow(teamFocusId, roomId);
        TeamFocusMember member = memberRepository.findByTeamFocusIdAndUserId(teamFocusId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "�㲻�ڸö�����"));
        if (member.getEndedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "���Ѿ������˶���רע");
        }
        member.setEndedAt(LocalDateTime.now());
        member.setDurationSeconds(Duration.between(member.getJoinedAt(), member.getEndedAt()).getSeconds());
        memberRepository.save(member);
        finishIfNeeded(focus);
        broadcast(focus);
        return toResponse(focus);
    }

    @Transactional
    public TeamFocusListResponse status(Long roomId) {
        // �ε�����������ƻ�ʱ�䵽��
        teamFocusRepository.findFirstByRoomIdAndStatusOrderByStartedAtDesc(roomId, TeamFocusStatus.ACTIVE)
                .ifPresent(this::finishIfNeeded);
        TeamFocus active = teamFocusRepository
                .findFirstByRoomIdAndStatusOrderByStartedAtDesc(roomId, TeamFocusStatus.ACTIVE)
                .orElse(null);
        List<TeamFocusResponse> recent = teamFocusRepository
                .findTop5ByRoomIdAndStatusOrderByEndedAtDesc(roomId, TeamFocusStatus.FINISHED).stream()
                .map(this::toResponse)
                .toList();
        return new TeamFocusListResponse(active == null ? null : toResponse(active), recent);
    }

    private TeamFocus getActiveOrThrow(Long teamFocusId, Long roomId) {
        TeamFocus focus = teamFocusRepository.findById(teamFocusId)
                .filter(f -> f.getRoom().getId().equals(roomId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "��רע�����ڣ�"));
        if (focus.getStatus() != TeamFocusStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "�ö���רע�ѽ���");
        }
        return focus;
    }

    private void addMember(TeamFocus focus, User user) {
        TeamFocusMember member = new TeamFocusMember();
        member.setTeamFocus(focus);
        member.setUser(user);
        member.setJoinedAt(LocalDateTime.now());
        memberRepository.save(member);
    }

    private void finishIfNeeded(TeamFocus focus) {
        if (focus.getStatus() != TeamFocusStatus.ACTIVE) {
            return;
        }
        List<TeamFocusMember> members = memberRepository.findByTeamFocusIdOrderByJoinedAtAsc(focus.getId());
        boolean everyoneStopped = members.stream().allMatch(m -> m.getEndedAt() != null);
        LocalDateTime plannedEnd = focus.getStartedAt().plusMinutes(focus.getPlannedMinutes());
        boolean timeUp = LocalDateTime.now().isAfter(plannedEnd) || LocalDateTime.now().equals(plannedEnd);
        if (!everyoneStopped && !timeUp) {
            return;
        }
        LocalDateTime endedAt = LocalDateTime.now();
        if (timeUp) {
            endedAt = plannedEnd;
            for (TeamFocusMember m : members) {
                if (m.getEndedAt() == null) {
                    m.setEndedAt(plannedEnd);
                    m.setDurationSeconds(Math.max(0,
                            Duration.between(m.getJoinedAt(), plannedEnd).getSeconds()));
                    memberRepository.save(m);
                }
            }
        } else {
            endedAt = members.stream().map(TeamFocusMember::getEndedAt).max(LocalDateTime::compareTo).orElse(endedAt);
        }
        focus.setEndedAt(endedAt);
        focus.setStatus(TeamFocusStatus.FINISHED);
        teamFocusRepository.save(focus);

        for (TeamFocusMember m : members) {
            long seconds = m.getDurationSeconds() == null ? 0 : m.getDurationSeconds();
            if (seconds < 900) {
                continue;
            }
            User u = m.getUser();
            long minutes = Math.max(1, seconds / 60);
            activityService.record(u.getId(), u.getUsername(), "TEAM_FOCUS_DONE",
                    "�͡�" + focus.getRoom().getName() + "����Ҷ�һ������� " + minutes + " ���ӵĶ���רע");
            notificationService.create(u.getId(), "TEAM_FOCUS", "����רע���� 🎉",
                    "����Ҷӹ�ͬ����� " + minutes + " ���ӵĶ���רע", focus.getRoom().getId(),
                    "/rooms/" + focus.getRoom().getId());
            gamificationService.awardTeamFocusBadge(u);
        }
    }

    private void broadcast(TeamFocus focus) {
        messagingTemplate.convertAndSend("/topic/rooms/" + focus.getRoom().getId() + "/team-focus",
                toResponse(focus));
    }

    private TeamFocusResponse toResponse(TeamFocus focus) {
        List<TeamFocusResponse.Member> members = memberRepository
                .findByTeamFocusIdOrderByJoinedAtAsc(focus.getId()).stream()
                .map(m -> new TeamFocusResponse.Member(m.getUser().getUsername(), m.getJoinedAt(),
                        m.getEndedAt(), m.getDurationSeconds(), m.getEndedAt() != null))
                .toList();
        long remaining = 0;
        if (focus.getStatus() == TeamFocusStatus.ACTIVE) {
            remaining = Math.max(0, Duration.between(LocalDateTime.now(),
                    focus.getStartedAt().plusMinutes(focus.getPlannedMinutes())).getSeconds());
        }
        return new TeamFocusResponse(focus.getId(), focus.getRoom().getId(),
                focus.getStatus().name(), focus.getStartedAt(), focus.getPlannedMinutes(),
                focus.getEndedAt(), remaining, members);
    }

    private void requireMember(Long roomId, Long userId) {
        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "���ȼ���÷���");
        }
    }
}
