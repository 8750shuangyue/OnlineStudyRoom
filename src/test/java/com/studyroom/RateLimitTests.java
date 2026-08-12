package com.studyroom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ratelimitdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RateLimitTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authEndpointRateLimited() throws Exception {
        int limitedAt = -1;
        for (int i = 1; i <= 22; i++) {
            int status = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"x\",\"password\":\"secret123\"}"))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                limitedAt = i;
                break;
            }
        }
        assertEquals(21, limitedAt, "认证接口每 IP 每分钟限 20 次，第 21 次应返回 429");
    }
}
