package com.studyroom.team;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms/{roomId}/team-focus")
public class TeamFocusController extends CurrentUserSupport {

    public record StartRequest(
            @Min(value = 5, message = "专注时长不能低于 5 分钟")
            @Max(value = 180, message = "专注时长不能超过 180 分钟")
            Integer plannedMinutes) {
    }

    private final TeamFocusService teamFocusService;

    public TeamFocusController(TeamFocusService teamFocusService, UserRepository userRepository) {
        super(userRepository);
        this.teamFocusService = teamFocusService;
    }

    @GetMapping
    public TeamFocusListResponse status(@PathVariable Long roomId) {
        return teamFocusService.status(roomId);
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamFocusResponse start(@PathVariable Long roomId,
                                   @RequestBody(required = false) StartRequest request,
                                   Authentication authentication) {
        Integer plannedMinutes = request == null ? null : request.plannedMinutes();
        return teamFocusService.start(currentUser(authentication), roomId, plannedMinutes);
    }

    @PostMapping("/{teamFocusId}/join")
    public TeamFocusResponse join(@PathVariable Long roomId,
                                  @PathVariable Long teamFocusId,
                                  Authentication authentication) {
        return teamFocusService.join(currentUser(authentication), roomId, teamFocusId);
    }

    @PostMapping("/{teamFocusId}/stop")
    public TeamFocusResponse stop(@PathVariable Long roomId,
                                  @PathVariable Long teamFocusId,
                                  Authentication authentication) {
        return teamFocusService.stop(currentUser(authentication), roomId, teamFocusId);
    }

}
