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
 * 房间禁言：房主可禁言/解除，非房主 403，非成员 400。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mutetestdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RoomMuteTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ownerCanMuteAndUnmute() throws Exception {
        String owner = register("muteowner");
        String member = register("mutemember");
        String stranger = register("mutestranger");
        long roomId = createRoom(owner);
        joinRoom(member, roomId);

        // 非房主禁言 → 403
        mockMvc.perform(post("/api/rooms/" + roomId + "/members/mutemember/mute")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":30}"))
                .andExpect(status().isForbidden());

        // 房主禁言 → 204
        mockMvc.perform(post("/api/rooms/" + roomId + "/members/mutemember/mute")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":30}"))
                .andExpect(status().isNoContent());

        // 解除禁言（0 分钟）→ 204
        mockMvc.perform(post("/api/rooms/" + roomId + "/members/mutemember/mute")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":0}"))
                .andExpect(status().isNoContent());

        // 非房间成员 → 400
        mockMvc.perform(post("/api/rooms/" + roomId + "/members/mutestranger/mute")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":30}"))
                .andExpect(status().isBadRequest());

        // 陌生用户访问房间接口 → 403（非成员不能进房）
        mockMvc.perform(post("/api/rooms/" + roomId + "/members/mutemember/mute")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":30}"))
                .andExpect(status().isForbidden());
    }

    private String register(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    private long createRoom(String token) throws Exception {
        String json = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"mute-room\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private void joinRoom(String token, long roomId) throws Exception {
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
