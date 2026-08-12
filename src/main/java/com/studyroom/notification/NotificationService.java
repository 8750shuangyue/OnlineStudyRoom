package com.studyroom.notification;

import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void createForUsername(String username, String type, String title,
                                  String body, Long roomId, String link) {
        userRepository.findByUsername(username).ifPresent(user ->
                create(user.getId(), type, title, body, roomId, link));
    }

    @Transactional
    public void create(Long userId, String type, String title, String body, Long roomId, String link) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRoomId(roomId);
        notification.setLink(link);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> list(Long userId, int limit) {
        int size = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在"));
        if (!notification.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作自己的通知");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

}
