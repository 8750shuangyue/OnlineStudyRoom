package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 总结的权限与状态校验测试（不实际调用 DeepSeek，避免测试依赖外部服务）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aidb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AiFlowTests {

    @Autowired
    private MockMvc mockMvc;

    private String registerAndGetToken(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void aiSummaryGuards() throws Exception {
        String aliceToken = registerAndGetToken("alice");
        String bobToken = registerAndGetToken("bob");

        // alice 创建房间并开始专注（不结束）
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AI 自习室\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        String startJson = mockMvc.perform(post("/api/sessions/start")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + roomId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long sessionId = ((Number) JsonPath.read(startJson, "$.id")).longValue();

        // 会话进行中：总结 → 409
        mockMvc.perform(post("/api/ai/sessions/" + sessionId + "/summary")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isConflict());

        // 别人的会话：总结 → 403
        mockMvc.perform(post("/api/ai/sessions/" + sessionId + "/summary")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        // 不存在的会话 → 404
        mockMvc.perform(post("/api/ai/sessions/9999/summary")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }
}
