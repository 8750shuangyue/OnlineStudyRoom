package com.studyroom.notification;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController extends CurrentUserSupport {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService,
                                  UserRepository userRepository) {
        super(userRepository);
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "50") int limit,
                                           Authentication authentication) {
        return notificationService.list(currentUser(authentication).getId(), limit).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(Authentication authentication) {
        return Map.of("count", notificationService.unreadCount(currentUser(authentication).getId()));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Authentication authentication) {
        notificationService.markAllRead(currentUser(authentication).getId());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id, Authentication authentication) {
        notificationService.markRead(id, currentUser(authentication).getId());
    }

}
