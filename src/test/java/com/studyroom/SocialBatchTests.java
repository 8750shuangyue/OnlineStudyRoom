package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import com.studyroom.realtime.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批次三：消息已读回执 / 房间周挑战 / 房间推荐。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:socialdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SocialBatchTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ChatService chatService;

    private String register(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void readReceiptsCountReaders() throws Exception {
        String alice = register("readalice");
        String bob = register("readbob");
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"回执房间\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk());

        chatService.send(roomId, "readalice", "大家好");

        mockMvc.perform(get("/api/rooms/" + roomId + "/messages?limit=50")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].readCount").value(0));

        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/rooms/" + roomId + "/read")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/" + roomId + "/messages?limit=50")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].readCount").value(2));
    }

    @Test
    void weeklyChallengeProgress() throws Exception {
        String token = register("chaluser");
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"挑战房间\",\"weeklyGoalMinutes\":60}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        String startJson = mockMvc.perform(post("/api/sessions/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + roomId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long sessionId = ((Number) JsonPath.read(startJson, "$.id")).longValue();
        jdbc.update("update study_sessions set started_at = dateadd('MINUTE', -20, started_at) where id = ?",
                sessionId);
        mockMvc.perform(post("/api/sessions/" + sessionId + "/stop")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/" + roomId + "/challenge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalMinutes").value(60))
                .andExpect(jsonPath("$.totalMinutes").value(20))
                .andExpect(jsonPath("$.progressPercent").value(33))
                .andExpect(jsonPath("$.achieved").value(false));
    }

    @Test
    void recommendedRoomsExcludeJoined() throws Exception {
        String alice = register("recalice");
        String bob = register("recbob");
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"推荐房间\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        mockMvc.perform(get("/api/rooms/recommended")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '推荐房间')]").exists());

        mockMvc.perform(get("/api/rooms/recommended")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '推荐房间')]").isEmpty());
    }
}
