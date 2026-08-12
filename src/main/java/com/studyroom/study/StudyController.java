package com.studyroom.study;

import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class StudyController {

    private final StudyService studyService;
    private final UserRepository userRepository;

    public StudyController(StudyService studyService, UserRepository userRepository) {
        this.studyService = studyService;
        this.userRepository = userRepository;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public StudySessionResponse start(@Valid @RequestBody StudySessionRequest request,
                                      Authentication authentication) {
        return studyService.start(currentUser(authentication), request.roomId(), request.taskId());
    }

    @PostMapping("/sync-start")
    @ResponseStatus(HttpStatus.CREATED)
    public StudySessionResponse syncStart(@Valid @RequestBody StudySessionRequest request,
                                          Authentication authentication) {
        return studyService.syncStart(currentUser(authentication), request.roomId());
    }

    @PostMapping("/{id}/stop")
    public StudySessionResponse stop(@PathVariable Long id, Authentication authentication) {
        return studyService.stop(currentUser(authentication), id);
    }

    @GetMapping
    public List<StudySessionResponse> mySessions(Authentication authentication) {
        return studyService.mySessions(currentUser(authentication));
    }

    @GetMapping("/active")
    public StudySessionResponse active(Authentication authentication) {
        return studyService.activeSession(currentUser(authentication));
    }

    /** 保存专注心得。 */
    @PutMapping("/{id}/reflection")
    public StudySessionResponse reflection(@PathVariable Long id,
                                           @Valid @RequestBody ReflectionRequest request,
                                           Authentication authentication) {
        return studyService.updateReflection(currentUser(authentication), id, request.text());
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }
}
