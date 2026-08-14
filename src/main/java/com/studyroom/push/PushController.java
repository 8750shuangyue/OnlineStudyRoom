package com.studyroom.push;

import com.studyroom.common.CurrentUserSupport;
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

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController extends CurrentUserSupport {

    public record SubscribeRequest(
            @NotBlank(message = "缺少 endpoint") String endpoint,
            @NotBlank(message = "缺少 p256dh") String p256dh,
            @NotBlank(message = "缺少 auth") String auth) {
    }

    public record UnsubscribeRequest(
            @NotBlank(message = "缺少 endpoint") String endpoint) {
    }

    private final WebPushService webPushService;

    public PushController(WebPushService webPushService, UserRepository userRepository) {
        super(userRepository);
        this.webPushService = webPushService;
    }

    @GetMapping("/vapid-key")
    public Map<String, String> vapidKey() {
        return Map.of("publicKey", webPushService.vapidPublicKey());
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody SubscribeRequest request, Authentication authentication) {
        webPushService.subscribe(currentUser(authentication), request.endpoint(), request.p256dh(), request.auth());
    }

    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody UnsubscribeRequest request, Authentication authentication) {
        webPushService.unsubscribe(currentUser(authentication), request.endpoint());
    }
}
