package com.studyroom.profile;

import com.studyroom.gamification.AchievementResponse;
import com.studyroom.gamification.GamificationService;
import com.studyroom.study.StudySession;
import com.studyroom.study.StudySessionRepository;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final UserRepository userRepository;
    private final GamificationService gamificationService;
    private final StudySessionRepository studySessionRepository;

    public ProfileController(UserRepository userRepository,
                             GamificationService gamificationService,
                             StudySessionRepository studySessionRepository) {
        this.userRepository = userRepository;
        this.gamificationService = gamificationService;
        this.studySessionRepository = studySessionRepository;
    }

    /** 查看任意用户主页（登录后即可访问）。 */
    @GetMapping("/{username}")
    @Transactional(readOnly = true)
    public ProfileResponse profile(@PathVariable String username, Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        AchievementResponse achievements = gamificationService.achievements(user);
        List<RecentSession> sessions = studySessionRepository
                .findByUserIdOrderByStartedAtDesc(user.getId(), PageRequest.of(0, 10)).stream()
                .map(this::toRecent)
                .toList();
        return new ProfileResponse(user.getUsername(), achievements.stats(),
                achievements.badges(), achievements.titles(), sessions);
    }

    private RecentSession toRecent(StudySession session) {
        return new RecentSession(session.getId(), session.getRoom().getName(),
                session.getStartedAt(), session.getEndedAt(),
                session.getDurationSeconds(), session.getReflection());
    }
}
