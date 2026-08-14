package com.studyroom.checkin;

import com.studyroom.checkin.CheckInService.CheckInStatus;
import com.studyroom.common.CurrentUserSupport;
import com.studyroom.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkins")
public class CheckInController extends CurrentUserSupport {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService, UserRepository userRepository) {
        super(userRepository);
        this.checkInService = checkInService;
    }

    @GetMapping
    public CheckInStatus status(Authentication authentication) {
        return checkInService.status(currentUser(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckInStatus checkIn(Authentication authentication) {
        return checkInService.checkIn(currentUser(authentication));
    }
}
