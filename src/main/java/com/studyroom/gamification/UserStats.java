package com.studyroom.gamification;

import com.studyroom.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "user_stats")
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private long xp;

    @Column(nullable = false)
    private int streak;

    @Column(nullable = false)
    private int bestStreak;

    /** 最近一次完成专注的日期，用于计算连续打卡 */
    private LocalDate lastFocusDate;

    @Column(nullable = false)
    private int distinctDays;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public long getXp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = xp;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public void setBestStreak(int bestStreak) {
        this.bestStreak = bestStreak;
    }

    public LocalDate getLastFocusDate() {
        return lastFocusDate;
    }

    public void setLastFocusDate(LocalDate lastFocusDate) {
        this.lastFocusDate = lastFocusDate;
    }

    public int getDistinctDays() {
        return distinctDays;
    }

    public void setDistinctDays(int distinctDays) {
        this.distinctDays = distinctDays;
    }
}
