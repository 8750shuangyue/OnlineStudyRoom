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
 * Web Push 订阅生命周期：VAPID 公钥、订阅（幂等 upsert）、退订。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pushdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PushFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pushSubscriptionLifecycle() throws Exception {
        String token = register("pushuser");

        mockMvc.perform(get("/api/push/vapid-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isNotEmpty());

        mockMvc.perform(post("/api/push/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example.com/abc\",\"p256dh\":\"AAA\",\"auth\":\"BBB\"}"))
                .andExpect(status().isNoContent());

        // 重复订阅同 endpoint 视为更新（幂等）
        mockMvc.perform(post("/api/push/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example.com/abc\",\"p256dh\":\"AAA2\",\"auth\":\"BBB2\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/push/unsubscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example.com/abc\"}"))
                .andExpect(status().isNoContent());
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
