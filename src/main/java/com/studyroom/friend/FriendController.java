package com.studyroom.friend;

import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    public record SendRequest(
            @NotBlank(message = "请指定用户名")
            String username) {
    }

    private final FriendService friendService;
    private final UserRepository userRepository;

    public FriendController(FriendService friendService, UserRepository userRepository) {
        this.friendService = friendService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<FriendResponse> friends(Authentication authentication) {
        return friendService.friends(currentUser(authentication));
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse sendRequest(@Valid @RequestBody SendRequest dto,
                                             Authentication authentication) {
        return friendService.sendRequest(currentUser(authentication), dto.username().trim());
    }

    @GetMapping("/requests")
    public List<FriendRequestResponse> incomingRequests(Authentication authentication) {
        return friendService.incomingRequests(currentUser(authentication));
    }

    @PostMapping("/requests/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable Long id, Authentication authentication) {
        friendService.acceptRequest(currentUser(authentication), id);
    }

    @PostMapping("/requests/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable Long id, Authentication authentication) {
        friendService.rejectRequest(currentUser(authentication), id);
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String username, Authentication authentication) {
        friendService.removeFriend(currentUser(authentication), username);
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }
}
