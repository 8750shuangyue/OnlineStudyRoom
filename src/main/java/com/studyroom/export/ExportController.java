package com.studyroom.export;

import com.studyroom.study.StudySession;
import com.studyroom.study.StudySessionRepository;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;

    public ExportController(StudySessionRepository studySessionRepository, UserRepository userRepository) {
        this.studySessionRepository = studySessionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping(value = "/sessions.csv", produces = "text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportSessions(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        List<StudySession> sessions = studySessionRepository.findByUserIdOrderByStartedAtDesc(user.getId());
        StringBuilder csv = new StringBuilder("开始时间,结束时间,房间,时长(分钟)\n");
        for (StudySession s : sessions) {
            if (s.getEndedAt() == null) {
                continue;
            }
            String roomName = s.getRoom().getName().replace("\"", "\"\"");
            csv.append(s.getStartedAt()).append(',')
                    .append(s.getEndedAt()).append(',')
                    .append('"').append(roomName).append('"').append(',')
                    .append(s.getDurationSeconds() == null ? 0 : s.getDurationSeconds() / 60)
                    .append('\n');
        }
        // 加 UTF-8 BOM，Excel 直接打开中文不乱码
        byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("study-sessions.csv", StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
