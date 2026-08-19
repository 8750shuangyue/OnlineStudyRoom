package com.studyroom.announcement;

import com.studyroom.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统公告：由配置的管理员账号发布，普通用户只读。
 */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    @Value("${app.admin.username:}")
    private String adminUsername;

    public AnnouncementService(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    public boolean isAdmin(String username) {
        return adminUsername != null && !adminUsername.isBlank() && adminUsername.equals(username);
    }

    @Transactional(readOnly = true)
    public List<Announcement> list() {
        return announcementRepository.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional
    public Announcement create(User admin, String title, String content) {
        if (!isAdmin(admin.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有发布公告的权限");
        }
        Announcement announcement = new Announcement();
        announcement.setTitle(title.trim());
        announcement.setContent(content.trim());
        announcement.setCreatedAt(LocalDateTime.now());
        return announcementRepository.save(announcement);
    }
}
