package com.studyroom.backup;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * H2 在线备份：运行中也能通过 BACKUP TO 生成完整数据库快照。
 */
@Service
public class BackupService {

    private static final int KEEP = 14;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JdbcTemplate jdbcTemplate;

    public BackupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BackupResponse backup() {
        try {
            Path backupDir = Paths.get(System.getProperty("user.dir"), "backups");
            Files.createDirectories(backupDir);
            String name = "studyroom-" + LocalDateTime.now().format(STAMP) + ".zip";
            String sqlPath = backupDir.resolve(name).toAbsolutePath().toString().replace("\\", "/");
            jdbcTemplate.execute("BACKUP TO '" + sqlPath + "'");
            cleanup(backupDir);
            Path file = backupDir.resolve(name);
            return new BackupResponse(file.toAbsolutePath().toString(), Files.size(file));
        } catch (IOException e) {
            throw new IllegalStateException("备份目录创建失败", e);
        }
    }

    private void cleanup(Path backupDir) throws IOException {
        List<Path> archives = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "studyroom-*.zip")) {
            for (Path path : stream) {
                archives.add(path);
            }
        }
        archives.sort(Comparator.comparing(Path::getFileName));
        for (int i = 0; i + KEEP < archives.size(); i++) {
            Files.deleteIfExists(archives.get(i));
        }
    }
}
