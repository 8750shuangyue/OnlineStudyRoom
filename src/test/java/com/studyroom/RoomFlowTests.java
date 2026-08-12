package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:roomdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RoomFlowTests {

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
    void roomCreateJoinLeaveFlow() throws Exception {
        String aliceToken = registerAndGetToken("alice");
        String bobToken = registerAndGetToken("bob");

        // 未登录创建房间 → 401
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java 自习室\"}"))
                .andExpect(status().isUnauthorized());

        // alice 创建房间，自动成为成员
        String createJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java 自习室\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Java 自习室"))
                .andExpect(jsonPath("$.ownerUsername").value("alice"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(createJson, "$.id")).longValue();

        // 房间列表包含新房间
        mockMvc.perform(get("/api/rooms")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(roomId));

        // bob 加入房间
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));

        // bob 重复加入 → 409
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isConflict());

        // 房间详情包含两个成员
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));

        // bob 退出房间
        mockMvc.perform(post("/api/rooms/" + roomId + "/leave")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1));

        // bob 的“我的房间”为空
        mockMvc.perform(get("/api/rooms/mine")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
