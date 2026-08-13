package com.studyroom.activity;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feed")
public class ActivityController extends CurrentUserSupport {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService, UserRepository userRepository) {
        super(userRepository);
        this.activityService = activityService;
    }

    @GetMapping
    public List<ActivityResponse> feed(Authentication authentication) {
        User user = currentUser(authentication);
        return activityService.feed(user.getId());
    }
}
