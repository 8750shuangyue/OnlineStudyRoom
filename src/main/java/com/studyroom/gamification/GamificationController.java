package com.studyroom.gamification;

import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class GamificationController {

    private final GamificationService gamificationService;
    private final GoalService goalService;
    private final UserRepository userRepository;

    public GamificationController(GamificationService gamificationService,
                                  GoalService goalService,
                                  UserRepository userRepository) {
        this.gamificationService = gamificationService;
        this.goalService = goalService;
        this.userRepository = userRepository;
    }

    @GetMapping("/achievements")
    public AchievementResponse achievements(Authentication authentication) {
        return gamificationService.achievements(currentUser(authentication));
    }

    @GetMapping("/goals")
    public GoalResponse goals(Authentication authentication) {
        return goalService.getGoal(currentUser(authentication));
    }

    @PutMapping("/goals")
    public GoalResponse updateGoal(@Valid @RequestBody GoalRequest request,
                                   Authentication authentication) {
        return goalService.updateGoal(currentUser(authentication), request.goalMinutes());
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }
}
