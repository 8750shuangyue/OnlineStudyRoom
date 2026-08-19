package com.studyroom.announcement;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/announcements")
public class AnnouncementController extends CurrentUserSupport {

    public record CreateRequest(
            @NotBlank(message = "公告标题不能为空")
            @Size(max = 200, message = "标题不能超过 200 字")
            String title,
            @NotBlank(message = "公告内容不能为空")
            @Size(max = 2000, message = "内容不能超过 2000 字")
            String content) {
    }

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService, UserRepository userRepository) {
        super(userRepository);
        this.announcementService = announcementService;
    }

    @GetMapping
    public List<Announcement> list(Authentication authentication) {
        return announcementService.list();
    }

    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public Announcement create(@Valid @RequestBody CreateRequest request, Authentication authentication) {
        return announcementService.create(currentUser(authentication), request.title(), request.content());
    }
}
