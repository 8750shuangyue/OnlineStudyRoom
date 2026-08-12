package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:gamifiedb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class GamificationFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private void backdateSession(long sessionId) {
        jdbc.update("update study_sessions set started_at = dateadd('MINUTE', -20, started_at) where id = ?",
                sessionId);
    }

    private String registerAndGetToken(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void achievementsGoalsAndLeaderboard() throws Exception {
        String alice = registerAndGetToken("alice");

        // 建房间并完成一次专注
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"成就自习室\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        String startJson = mockMvc.perform(post("/api/sessions/start")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + roomId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long sessionId = ((Number) JsonPath.read(startJson, "$.id")).longValue();
        backdateSession(sessionId);
        mockMvc.perform(post("/api/sessions/" + sessionId + "/stop")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk());

        // 成就：经验、等级、首次专注徽章
        mockMvc.perform(get("/api/achievements")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"level\":1")))
                .andExpect(content().string(containsString("\"streak\":1")))
                .andExpect(content().string(containsString("\"totalSessions\":1")))
                .andExpect(content().string(containsString("\"code\":\"FIRST_FOCUS\"")))
                .andExpect(content().string(containsString("\"earned\":true")));

        // 每日目标：默认 120，更新为 60
        mockMvc.perform(get("/api/goals")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"goalMinutes\":120")))
                .andExpect(content().string(containsString("\"todayMinutes\":")));
        mockMvc.perform(put("/api/goals")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalMinutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"goalMinutes\":60")));

        // 排行榜：房间榜与全站榜（全部/本周）都包含 alice
        mockMvc.perform(get("/api/rooms/" + roomId + "/leaderboard")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
        mockMvc.perform(get("/api/rooms/" + roomId + "/leaderboard?period=week")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
        mockMvc.perform(get("/api/leaderboard/global?period=all")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
        mockMvc.perform(get("/api/leaderboard/global?period=today")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }
}
