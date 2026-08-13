package com.studyroom.stats;

import com.studyroom.common.CurrentUserSupport;
import com.studyroom.user.User;
import com.studyroom.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import com.studyroom.stats.DailyStat;
import com.studyroom.stats.TimeBucket;
import com.studyroom.stats.WeeklyReport;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StatsController extends CurrentUserSupport {

    private final StatsService statsService;

    public StatsController(StatsService statsService, UserRepository userRepository) {
        super(userRepository);
        this.statsService = statsService;
    }

    @GetMapping("/stats/me")
    public StatsResponse myStats(Authentication authentication) {
        return statsService.myStats(currentUser(authentication));
    }

    @GetMapping("/rooms/{roomId}/leaderboard")
    public List<LeaderboardEntry> roomLeaderboard(@PathVariable Long roomId,
                                                  @RequestParam(required = false) String period,
                                                  @RequestParam(required = false) String metric) {
        return statsService.roomLeaderboard(roomId, period, metric);
    }

    @GetMapping("/leaderboard/global")
    public List<LeaderboardEntry> globalLeaderboard(@RequestParam(required = false) String period,
                                                    @RequestParam(required = false) String metric) {
        return statsService.globalLeaderboard(period, metric);
    }

    @GetMapping("/stats/trend")
    public List<DailyStat> trend(@RequestParam(defaultValue = "14") int days,
                                 Authentication authentication) {
        return statsService.dailyTrend(currentUser(authentication), days);
    }

    @GetMapping("/stats/heatmap")
    public List<DailyStat> heatmap(Authentication authentication) {
        return statsService.dailyTrend(currentUser(authentication), 90);
    }

    @GetMapping("/stats/weekly")
    public WeeklyReport weekly(Authentication authentication) {
        return statsService.weekly(currentUser(authentication));
    }

    @GetMapping("/stats/time-analysis")
    public List<TimeBucket> timeAnalysis(Authentication authentication) {
        return statsService.timeAnalysis(currentUser(authentication));
    }

}
