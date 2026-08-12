package com.studyroom.backup;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地在线备份（适合开发阶段；部署后应替换为数据库专用备份方案）。
 */
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BackupResponse backup() {
        return backupService.backup();
    }
}
