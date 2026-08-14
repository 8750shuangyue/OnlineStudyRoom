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

/**
 * 每日任务：任务列表、完成任务后领取 XP、重复领取 409、未完成领取 400。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dailytaskdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class DailyTaskFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void claimCheckinTaskOnce() throws Exception {
        String token = register("taskuser");

        mockMvc.perform(get("/api/tasks/daily").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        mockMvc.perform(post("/api/checkins").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tasks/daily/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"checkin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rewarded").value(true));

        mockMvc.perform(post("/api/tasks/daily/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"checkin\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/tasks/daily/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"focus\"}"))
                .andExpect(status().isBadRequest());
    }

    private String register(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
