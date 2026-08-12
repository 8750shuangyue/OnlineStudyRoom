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

/**
 * 知识卡片复习：创建 / 到期 / 间隔重复调度 / 删除。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:carddb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FlashcardFlowTests {

    @Autowired
    private MockMvc mockMvc;

    private String register(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void cardLifecycle() throws Exception {
        String token = register("carduser");

        String created = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"front\":\"导数定义\",\"back\":\"瞬时变化率\","
                                + "\"sourceType\":\"NOTE\",\"sourceId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.due").value(true))
                .andReturn().getResponse().getContentAsString();
        long cardId = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(get("/api/cards/due-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/cards/due")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].front").value("导数定义"));

        String reviewed = mockMvc.perform(post("/api/cards/" + cardId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"NORMAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.due").value(false))
                .andReturn().getResponse().getContentAsString();
        String dueAt = JsonPath.read(reviewed, "$.dueAt");
        org.junit.jupiter.api.Assertions.assertNotNull(dueAt);

        mockMvc.perform(get("/api/cards/due-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(delete("/api/cards/" + cardId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
