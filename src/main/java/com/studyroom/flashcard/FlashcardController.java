package com.studyroom.flashcard;

import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cards")
public class FlashcardController {

    private final FlashcardService flashcardService;
    private final UserRepository userRepository;

    public FlashcardController(FlashcardService flashcardService, UserRepository userRepository) {
        this.flashcardService = flashcardService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<FlashcardResponse> list(Authentication authentication) {
        return flashcardService.list(currentUser(authentication).getId());
    }

    @GetMapping("/due-count")
    public Map<String, Object> dueCount(Authentication authentication) {
        return Map.of("count", flashcardService.dueCount(currentUser(authentication).getId()));
    }

    @GetMapping("/due")
    public List<FlashcardResponse> due(Authentication authentication) {
        return flashcardService.dueCards(currentUser(authentication).getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlashcardResponse create(@Valid @RequestBody FlashcardRequest request,
                                    Authentication authentication) {
        return flashcardService.create(currentUser(authentication).getId(), request);
    }

    @PostMapping("/{id}/review")
    public FlashcardResponse review(@PathVariable Long id,
                                    @Valid @RequestBody ReviewRequest request,
                                    Authentication authentication) {
        return flashcardService.review(currentUser(authentication).getId(), id, request.rating());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        flashcardService.delete(currentUser(authentication).getId(), id);
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }
}
