package com.studyroom.gamification;

import com.studyroom.common.CurrentUserSupport;
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
public class GamificationController extends CurrentUserSupport {

    private final GamificationService gamificationService;
    private final GoalService goalService;

    public GamificationController(GamificationService gamificationService,
                                  GoalService goalService,
                                  UserRepository userRepository) {
        super(userRepository);
        this.gamificationService = gamificationService;
        this.goalService = goalService;
    }

    @GetMapping("/achievements")
    public AchievementResponse achievements(Authentication authentication) {
        User user = currentUser(authentication);
        gamificationService.settleSeasons(user);
        return gamificationService.achievements(user);
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

}
