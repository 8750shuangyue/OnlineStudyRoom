package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批次三收尾：组队专注 / 赛季结算徽章 / 公开名片。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:teamseason;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class TeamAndSeasonTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String register(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    private long createRoom(String token, String name) throws Exception {
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(roomJson, "$.id")).longValue();
    }

    private void joinRoom(String token, long roomId) throws Exception {
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void teamFocusFlowFinishesAndAwardsBadge() throws Exception {
        String alice = register("tfalice");
        String bob = register("tfbob");
        long roomId = createRoom(alice, "组队房");
        joinRoom(bob, roomId);

        String startJson = mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/start")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedMinutes\":25}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long teamId = ((Number) JsonPath.read(startJson, "$.id")).longValue();

        mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/" + teamId + "/join")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));

        // 队长先结束，队伍仍在进行
        mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/" + teamId + "/stop")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 回填 Bob 的加入时间，模拟已专注 20 分钟后再结束
        jdbc.update("""
                update team_focus_members set joined_at = dateadd('MINUTE', -20, joined_at)
                where team_focus_id = ? and user_id = (select id from users where username = 'tfbob')
                """, teamId);
        mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/" + teamId + "/stop")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));

        mockMvc.perform(get("/api/rooms/" + roomId + "/team-focus")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").doesNotExist())
                .andExpect(jsonPath("$.recent[0].status").value("FINISHED"))
                .andExpect(jsonPath("$.recent[0].members[0].durationSeconds").value(1200));

        mockMvc.perform(get("/api/achievements")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.badges[?(@.code == 'TEAM_FIRST')].earned",
                        Matchers.hasItem(true)));
    }

    @Test
    void teamFocusRejectsMoreThanSixMembers() throws Exception {
        String owner = register("tfowner");
        long roomId = createRoom(owner, "满员房");
        String[] others = {"tfm1", "tfm2", "tfm3", "tfm4", "tfm5", "tfm6"};
        String[] tokens = new String[others.length];
        for (int i = 0; i < others.length; i++) {
            tokens[i] = register(others[i]);
            joinRoom(tokens[i], roomId);
        }

        String startJson = mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/start")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedMinutes\":15}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long teamId = ((Number) JsonPath.read(startJson, "$.id")).longValue();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/" + teamId + "/join")
                            .header("Authorization", "Bearer " + tokens[i]))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/rooms/" + roomId + "/team-focus")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.active.members.length()").value(6));

        mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/" + teamId + "/join")
                        .header("Authorization", "Bearer " + tokens[5]))
                .andExpect(status().isConflict());
    }

    @Test
    void seasonSettlementAwardsPreviousWeekBadges() throws Exception {
        String token = register("seasonuser");
        long roomId = createRoom(token, "赛季房");
        LocalDateTime prevMonday = LocalDate.now().minusWeeks(1).with(DayOfWeek.MONDAY).atTime(9, 0);

        for (int i = 0; i < 3; i++) {
            String startJson = mockMvc.perform(post("/api/sessions/start")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roomId\":" + roomId + "}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long sessionId = ((Number) JsonPath.read(startJson, "$.id")).longValue();
            jdbc.update("update study_sessions set started_at = ? where id = ?",
                    Timestamp.valueOf(prevMonday.plusHours(i)), sessionId);
            mockMvc.perform(post("/api/sessions/" + sessionId + "/stop")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/achievements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonAwards[?(@.code == 'SEASON_60')]",
                        Matchers.hasSize(1)))
                .andExpect(jsonPath("$.currentSeason.seasonKey").isNotEmpty());
    }

    @Test
    void publicCardIsAccessibleWithoutAuth() throws Exception {
        register("carduser");
        mockMvc.perform(get("/api/users/carduser/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("carduser"))
                .andExpect(jsonPath("$.stats.level").isNumber())
                .andExpect(jsonPath("$.badges").isArray());
    }

    @Test
    void teamFocusRejectsInvalidTransitions() throws Exception {
        String owner = register("tferr_owner");
        String member = register("tferr_member");
        String stranger = register("tferr_stranger");
        long roomId = createRoom(owner, "conflict-room");
        joinRoom(member, roomId);

        // 非房间成员不能发起团队专注
        mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/start")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedMinutes\":15}"))
                .andExpect(status().isForbidden());

        String startJson = mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/start")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedMinutes\":15}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long teamId = ((Number) JsonPath.read(startJson, "$.id")).longValue();

        // 已有进行中的团队专注时不能再次发起
        mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/start")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedMinutes\":15}"))
                .andExpect(status().isConflict());

        // 成员重复加入被拒绝；非房间成员不能加入
        mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/" + teamId + "/join")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/" + teamId + "/join")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/rooms/" + roomId + "/team-focus/" + teamId + "/join")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownUserPublicCardReturns404() throws Exception {
        mockMvc.perform(get("/api/users/nobody-here/card"))
                .andExpect(status().isNotFound());
    }
}
