package com.studyroom.room;

import com.studyroom.notification.NotificationService;
import com.studyroom.stats.LeaderboardEntry;
import com.studyroom.stats.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 房间周挑战：每周日 20:30 结算本周专注排行，把结果推送给房间成员（站内信）。
 */
@Service
public class WeeklyChallengeService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyChallengeService.class);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final StatsService statsService;
    private final NotificationService notificationService;

    public WeeklyChallengeService(RoomRepository roomRepository,
                                  RoomMemberRepository roomMemberRepository,
                                  StatsService statsService,
                                  NotificationService notificationService) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.statsService = statsService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 30 20 * * 0")
    public void settleWeeklyChallenges() {
        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        LocalDateTime from = weekStart.atStartOfDay();
        for (Room room : roomRepository.findByWeeklyGoalMinutesGreaterThan(0)) {
            try {
                notifyRoomSummary(room, from);
            } catch (Exception e) {
                log.warn("周挑战结算失败 roomId={}: {}", room.getId(), e.getMessage());
            }
        }
    }

    private void notifyRoomSummary(Room room, LocalDateTime from) {
        List<LeaderboardEntry> top = statsService.roomLeaderboard(room.getId(), "week", "minutes");
        long totalMinutes = top.stream().mapToLong(LeaderboardEntry::value).sum();
        boolean goalMet = totalMinutes >= room.getWeeklyGoalMinutes();
        StringBuilder body = new StringBuilder();
        body.append("本周「").append(room.getName()).append("」")
                .append(goalMet ? "达成" : "未达成")
                .append("挑战目标 ").append(room.getWeeklyGoalMinutes()).append(" 分钟")
                .append("（全房间共 ").append(totalMinutes).append(" 分钟）\n");
        if (top.isEmpty()) {
            body.append("本周还没有专注记录，下周加油！");
        } else {
            String[] medals = {"🥇", "🥈", "🥉"};
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                LeaderboardEntry entry = top.get(i);
                body.append(medals[i]).append(' ').append(entry.username())
                        .append(' ').append(entry.value()).append(" 分钟\n");
            }
        }
        for (RoomMember member : roomMemberRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())) {
            notificationService.create(member.getUser().getId(), "WEEKLY_CHALLENGE",
                    "本周挑战结果", body.toString(), room.getId(),
                    "/rooms/" + room.getId());
        }
    }
}
