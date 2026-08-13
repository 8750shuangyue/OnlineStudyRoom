package com.studyroom.mistake;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/mistakes")
public class MistakeController extends CurrentUserSupport {

    private final MistakeService mistakeService;

    public MistakeController(MistakeService mistakeService, UserRepository userRepository) {
        super(userRepository);
        this.mistakeService = mistakeService;
    }

    @GetMapping
    public List<MistakeResponse> list(Authentication authentication) {
        return mistakeService.list(currentUser(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MistakeResponse create(@Valid @RequestBody MistakeRequest request,
                                  Authentication authentication) {
        return mistakeService.create(currentUser(authentication), request);
    }

    @PutMapping("/{id}")
    public MistakeResponse update(@PathVariable Long id,
                                  @Valid @RequestBody MistakeRequest request,
                                  Authentication authentication) {
        return mistakeService.update(currentUser(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        mistakeService.delete(currentUser(authentication), id);
    }

    /** 今日到期待复习的错题。 */
    @GetMapping("/reviews")
    public List<MistakeResponse> reviews(Authentication authentication) {
        return mistakeService.listDue(currentUser(authentication));
    }

    /** 今日到期错题数量。 */
    @GetMapping("/review-count")
    public java.util.Map<String, Object> reviewCount(Authentication authentication) {
        return java.util.Map.of("count", mistakeService.dueCount(currentUser(authentication)));
    }

    /** 提交复习结果（掌握/未掌握）。 */
    @PostMapping("/{id}/review")
    public MistakeResponse review(@PathVariable Long id,
                                  @Valid @RequestBody ReviewRequest request,
                                  Authentication authentication) {
        return mistakeService.review(currentUser(authentication), id, request.mastered());
    }

}
