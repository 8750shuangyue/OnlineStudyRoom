package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 房间邀请码：成员获取、非成员 403、按码加入、重复加入 409、无效码 404。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:invitecodedb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RoomInviteCodeTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void inviteCodeFlow() throws Exception {
        String owner = register("invowner");
        String member = register("invmember");
        String stranger = register("invstranger");
        long roomId = createRoom(owner);

        String invite = mockMvc.perform(get("/api/rooms/" + roomId + "/invite-code")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(invite, "$.code");
        assertEquals(8, code.length());

        mockMvc.perform(get("/api/rooms/" + roomId + "/invite-code")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/rooms/join-by-code")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId));

        mockMvc.perform(post("/api/rooms/join-by-code")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/rooms/join-by-code")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BADCODE1\"}"))
                .andExpect(status().isNotFound());
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
                        .content("{\"name\":\"invite-room\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
