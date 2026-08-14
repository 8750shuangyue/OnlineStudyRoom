package com.studyroom.dailytask;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.dailytask.DailyTaskService.TaskView;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/daily")
public class DailyTaskController extends CurrentUserSupport {

    public record ClaimRequest(
            @NotBlank(message = "缺少任务 key") String key) {
    }

    private final DailyTaskService dailyTaskService;

    public DailyTaskController(DailyTaskService dailyTaskService, UserRepository userRepository) {
        super(userRepository);
        this.dailyTaskService = dailyTaskService;
    }

    @GetMapping
    public List<TaskView> status(Authentication authentication) {
        return dailyTaskService.status(currentUser(authentication));
    }

    @PostMapping("/claim")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskView claim(@Valid @RequestBody ClaimRequest request, Authentication authentication) {
        return dailyTaskService.claim(currentUser(authentication), request.key());
    }
}
