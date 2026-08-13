package com.studyroom.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 轻量启动迁移：为已有表补齐新加的列（幂等，H2）。
 * Hibernate ddl-auto=update 偶尔不会为旧库补列，这里兜底。
 */
@Component
public class SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        ensureColumn("rooms", "focus_minutes", "INT DEFAULT 0");
        ensureColumn("rooms", "break_minutes", "INT DEFAULT 0");
        ensureColumn("rooms", "ai_tutor_enabled", "BOOLEAN DEFAULT FALSE");
        ensureColumn("rooms", "tutor_persona", "VARCHAR(200)");
        ensureColumn("rooms", "weekly_goal_minutes", "INT DEFAULT 0");
        ensureColumn("notes", "title", "VARCHAR(200)");
        ensureColumn("notes", "category", "VARCHAR(50)");
        ensureColumn("mistakes", "review_status", "VARCHAR(20) DEFAULT 'NEW'");
        ensureColumn("mistakes", "review_count", "INT DEFAULT 0");
        ensureColumn("mistakes", "next_review_at", "TIMESTAMP");
        ensureColumn("mistakes", "last_reviewed_at", "TIMESTAMP");
        ensureColumn("study_sessions", "reflection", "VARCHAR(2000)");
    }

    private void ensureColumn(String table, String column, String ddl) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from INFORMATION_SCHEMA.COLUMNS where TABLE_NAME = ? and COLUMN_NAME = ?",
                    Integer.class, table.toUpperCase(), column.toUpperCase());
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
            log.info("SchemaMigration: 已为表 {} 补列 {}", table, column);
        } catch (Exception e) {
            log.warn("SchemaMigration: 补列失败 {}.{}: {}", table, column, e.getMessage());
        }
    }
}
