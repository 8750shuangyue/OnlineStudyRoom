package com.studyroom.room;

import com.studyroom.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 30)
    private String category;

    /** 房间公告，展示在房间页顶部 */
    @Column(length = 1000)
    private String announcement;

    /** 私密房间的加入密码（为空表示公开房间） */
    @Column(length = 100)
    private String password;

    /** 软删除标记：解散房间后列表不再展示，学习记录保留 */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE NOT NULL")
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 房间默认单次专注时长（分钟），0 = 成员用自己的设置 */
    private Integer focusMinutes = 0;

    /** 房间默认休息时长（分钟），0 = 成员用自己的设置 */
    private Integer breakMinutes = 0;

    /** 是否开启 AI 房间助教 */
    @Column(nullable = false)
    private boolean aiTutorEnabled = false;

    /** 助教人设（提示词） */
    @Column(length = 200)
    private String tutorPersona;

    /** 本周房间挑战目标（分钟），0 = 未开启 */
    private Integer weeklyGoalMinutes = 0;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAnnouncement() {
        return announcement;
    }

    public void setAnnouncement(String announcement) {
        this.announcement = announcement;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getFocusMinutes() {
        return focusMinutes;
    }

    public void setFocusMinutes(Integer focusMinutes) {
        this.focusMinutes = focusMinutes;
    }

    public Integer getBreakMinutes() {
        return breakMinutes;
    }

    public void setBreakMinutes(Integer breakMinutes) {
        this.breakMinutes = breakMinutes;
    }

    public boolean isAiTutorEnabled() {
        return aiTutorEnabled;
    }

    public void setAiTutorEnabled(boolean aiTutorEnabled) {
        this.aiTutorEnabled = aiTutorEnabled;
    }

    public String getTutorPersona() {
        return tutorPersona;
    }

    public void setTutorPersona(String tutorPersona) {
        this.tutorPersona = tutorPersona;
    }

    public Integer getWeeklyGoalMinutes() {
        return weeklyGoalMinutes;
    }

    public void setWeeklyGoalMinutes(Integer weeklyGoalMinutes) {
        this.weeklyGoalMinutes = weeklyGoalMinutes;
    }
}
