package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:roommgmtdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RoomManagementTests {

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

    private long createRoom(String token, String name, String category, String password) throws Exception {
        String json = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"category\":\"" + category
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void roomManagementFlow() throws Exception {
        String alice = registerAndGetToken("alice");
        String bob = registerAndGetToken("bob");
        String carol = registerAndGetToken("carol");

        // 创建带标签 + 密码 + 公告的房间
        String createJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"考研冲刺\",\"category\":\"考研\","
                                + "\"password\":\"1234\",\"announcement\":\"一起加油\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("考研"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(createJson, "$.id")).longValue();

        // 密码错误（或无密码）不能加入
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden());
        // 正确密码加入
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));

        // 非房主不能改名
        mockMvc.perform(put("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\",\"category\":\"考研\",\"announcement\":\"x\"}"))
                .andExpect(status().isForbidden());

        // 房主改名 + 改公告 + 清除密码
        mockMvc.perform(put("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"冲刺自习室\",\"category\":\"考研\","
                                + "\"announcement\":\"新公告\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("冲刺自习室"))
                .andExpect(jsonPath("$.announcement").value("新公告"))
                .andExpect(jsonPath("$.hasPassword").value(false));

        // 非房主不能踢人
        mockMvc.perform(post("/api/rooms/" + roomId + "/kick")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carol\"}"))
                .andExpect(status().isForbidden());

        // carol 加入后被房主踢出
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + carol))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/" + roomId + "/kick")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carol\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));

        // 不能踢房主
        mockMvc.perform(post("/api/rooms/" + roomId + "/kick")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        // 房主转移给 bob
        mockMvc.perform(post("/api/rooms/" + roomId + "/transfer")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUsername").value("bob"));

        // 旧房主失去管理权，新房主可解散
        mockMvc.perform(delete("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isNoContent());

        // 解散后列表与详情不可见
        mockMvc.perform(get("/api/rooms")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound());

        // 搜索与分类筛选
        createRoom(alice, "Java 自习室", "编程", "");
        createRoom(alice, "雅思口语", "英语", "");
        mockMvc.perform(get("/api/rooms?search=java")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Java 自习室"));
        mockMvc.perform(get("/api/rooms?category=英语")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("雅思口语"));
        mockMvc.perform(get("/api/rooms/categories")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("编程"))
                .andExpect(jsonPath("$[1]").value("英语"));
    }
}
