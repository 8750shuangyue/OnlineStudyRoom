package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:taskdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class TaskAndDashboardTests {

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
    void taskBindingAndDashboard() throws Exception {
        String alice = registerAndGetToken("alice");

        // 建房间
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"看板自习室\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        // 建任务
        String taskJson = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"背 50 个单词\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.done").value(false))
                .andReturn().getResponse().getContentAsString();
        long taskId = ((Number) JsonPath.read(taskJson, "$.id")).longValue();

        // 绑定任务开始专注
        String startJson = mockMvc.perform(post("/api/sessions/start")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + roomId + ",\"taskId\":" + taskId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andReturn().getResponse().getContentAsString();
        long sessionId = ((Number) JsonPath.read(startJson, "$.id")).longValue();

        // 回拨开始时间使会话满 15 分钟后结束 → 任务自动完成
        backdateSession(sessionId);
        mockMvc.perform(post("/api/sessions/" + sessionId + "/stop")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].done").value(true));

        // 数据看板接口
        mockMvc.perform(get("/api/stats/trend?days=7")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"date\"")));
        mockMvc.perform(get("/api/stats/heatmap")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"date\"")));
        mockMvc.perform(get("/api/stats/weekly")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysActive").value(1L))
                .andExpect(jsonPath("$.totalSessions").value(1L));
        mockMvc.perform(get("/api/stats/time-analysis")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        // 任务删除
        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());
    }
}
