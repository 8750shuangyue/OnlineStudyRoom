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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:frienddb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FriendFlowTests {

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
    void friendRequestAndInviteFlow() throws Exception {
        String alice = registerAndGetToken("alice");
        String bob = registerAndGetToken("bob");
        String carol = registerAndGetToken("carol");

        // alice 向 bob 发好友请求
        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"));

        // 重复发送 → 409
        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\"}"))
                .andExpect(status().isConflict());

        // 不能添加自己 → 400
        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        // bob 看到收到的请求
        String requestsJson = mockMvc.perform(get("/api/friends/requests")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andReturn().getResponse().getContentAsString();
        long requestId = ((Number) JsonPath.read(requestsJson, "$[0].id")).longValue();

        // bob 接受
        mockMvc.perform(post("/api/friends/requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isNoContent());

        // 双方好友列表都包含对方
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("bob"));
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));

        // alice 建房间并邀请 bob
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"好友自习室\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        // 非好友 carol 不能被邀请
        mockMvc.perform(post("/api/rooms/" + roomId + "/invite")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carol\"}"))
                .andExpect(status().isBadRequest());

        // 邀请 bob
        mockMvc.perform(post("/api/rooms/" + roomId + "/invite")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomName").value("好友自习室"));

        // bob 看到邀请并接受 → 成为成员
        String invitesJson = mockMvc.perform(get("/api/invites")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromUsername").value("alice"))
                .andReturn().getResponse().getContentAsString();
        long inviteId = ((Number) JsonPath.read(invitesJson, "$[0].id")).longValue();

        mockMvc.perform(post("/api/invites/" + inviteId + "/accept")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));

        // 移除好友
        mockMvc.perform(delete("/api/friends/bob")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/friends")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
