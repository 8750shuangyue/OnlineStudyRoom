package com.studyroom.study;

import com.studyroom.room.Room;
import com.studyroom.room.RoomMemberRepository;
import com.studyroom.room.RoomRepository;
import com.studyroom.user.User;
import com.studyroom.gamification.GamificationService;
import com.studyroom.task.Task;
import com.studyroom.task.TaskRepository;
import com.studyroom.activity.ActivityService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StudyService {

    private final StudySessionRepository studySessionRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GamificationService gamificationService;
    private final TaskRepository taskRepository;
    private final ActivityService activityService;

    public StudyService(StudySessionRepository studySessionRepository,
                        RoomRepository roomRepository,
                        RoomMemberRepository roomMemberRepository,
                        SimpMessagingTemplate messagingTemplate,
                        GamificationService gamificationService,
                        TaskRepository taskRepository,
                        ActivityService activityService) {
        this.studySessionRepository = studySessionRepository;
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.messagingTemplate = messagingTemplate;
        this.gamificationService = gamificationService;
        this.taskRepository = taskRepository;
        this.activityService = activityService;
    }

    @Transactional
    public StudySessionResponse start(User user, Long roomId, Long taskId) {
        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先加入该房间");
        }
        if (studySessionRepository.findByUserIdAndEndedAtIsNull(user.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有进行中的专注会话，请先结束");
        }
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "房间不存在"));

        StudySession session = new StudySession();
        session.setUser(user);
        session.setRoom(room);
        session.setStartedAt(LocalDateTime.now());
        if (taskId != null) {
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
            if (!task.getUser().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能绑定自己的任务");
            }
            session.setTaskId(task.getId());
        }
        studySessionRepository.save(session);
        broadcastFocus(session, "START");
        return toResponse(session);
    }

    @Transactional
    public StudySessionResponse stop(User user, Long sessionId) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能结束自己的会话");
        }
        if (session.getEndedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会话已结束");
        }
        session.setEndedAt(LocalDateTime.now());
        session.setDurationSeconds(Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds());
        studySessionRepository.save(session);
        // 专注不足 15 分钟不算一次有效专注：不计经验/成就/任务完成
        boolean valid = session.getDurationSeconds() >= 900;
        if (valid) {
            if (session.getTaskId() != null) {
                taskRepository.findById(session.getTaskId()).ifPresent(task -> {
                    task.setDone(true);
                    task.setCompletedAt(LocalDateTime.now());
                    taskRepository.save(task);
                });
            }
            gamificationService.recordSessionCompleted(user, session);
            activityService.record(user.getId(), user.getUsername(), "FOCUS_DONE",
                    "在「" + session.getRoom().getName() + "」完成 "
                            + Math.max(1, session.getDurationSeconds() / 60) + " 分钟专注");
        }
        broadcastFocus(session, "STOP");
        return toResponse(session);
    }

    /**
     * 同步专注：自己开始，并向房间广播，邀请其他人一起开始。
     */
    @Transactional
    public StudySessionResponse syncStart(User user, Long roomId) {
        StudySessionResponse response = start(user, roomId, null);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/sync",
                new SyncMessage("SYNC_START", user.getUsername(), LocalDateTime.now()));
        return response;
    }

    @Transactional(readOnly = true)
    public List<StudySessionResponse> mySessions(User user) {
        return studySessionRepository.findByUserIdOrderByStartedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudySessionResponse activeSession(User user) {
        return studySessionRepository.findByUserIdAndEndedAtIsNull(user.getId())
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public StudySessionResponse updateReflection(User user, Long sessionId, String reflection) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能编辑自己的会话");
        }
        session.setReflection(trimToNull(reflection));
        return toResponse(studySessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public java.util.List<FocusEntry> roomFocusStatus(Long roomId) {
        return studySessionRepository.findByRoomIdAndEndedAtIsNull(roomId).stream()
                .map(s -> new FocusEntry(s.getUser().getUsername(), s.getId(), s.getStartedAt()))
                .toList();
    }

    private void broadcastFocus(StudySession session, String type) {
        messagingTemplate.convertAndSend("/topic/rooms/" + session.getRoom().getId() + "/focus",
                new FocusMessage(type, session.getUser().getUsername(), session.getId(), session.getStartedAt()));
    }

    private StudySessionResponse toResponse(StudySession session) {
        String status = session.getEndedAt() == null ? "ACTIVE" : "FINISHED";
        return new StudySessionResponse(session.getId(), session.getRoom().getId(),
                session.getRoom().getName(), status, session.getStartedAt(),
                session.getEndedAt(), session.getDurationSeconds(), session.getTaskId(),
                session.getReflection());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
