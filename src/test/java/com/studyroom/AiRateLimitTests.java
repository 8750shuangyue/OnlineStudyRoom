package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import com.studyroom.ai.AiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 接口按用户限流：同一用户 15 次/分钟，第 16 次返回 429；同 IP 的其他用户额度独立。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:airatelimitdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AiRateLimitTests {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class MockAiConfig {
        @Bean
        @Primary
        AiService aiService() {
            AiService mock = mock(AiService.class);
            when(mock.chatWithMemory(any(), anyString())).thenReturn("ok");
            return mock;
        }
    }

    @Test
    void aiEndpointLimitedPerUser() throws Exception {
        String alice = register("ailimitalice");
        String bob = register("ailimitbob");

        int limitedAt = -1;
        for (int i = 1; i <= 16; i++) {
            int status = mockMvc.perform(post("/api/ai/chat")
                            .header("Authorization", "Bearer " + alice)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"hi\"}"))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                limitedAt = i;
                break;
            }
        }
        assertEquals(16, limitedAt, "AI 接口按用户每分钟限 15 次，第 16 次应返回 429");

        // 同一 IP 下的另一个用户额度独立，不应被误伤
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk());
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
