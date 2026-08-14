package com.studyroom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全审计回归：AI 接口必须登录，旧的 /api/chat 别名已移除。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:secaudit;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SecurityAuditTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aiEndpointsRequireAuth() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/rag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oldChatAliasNoLongerPublic() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void spaClientRoutesNotBlockedByAuth() throws Exception {
        // 前端路由（/join/xxx、/help 等）不应被安全拦截成 401；
        // 有前端构建时返回 200（回退 index.html），CI 无前端构建时可能 404
        int joinStatus = mockMvc.perform(get("/join/ABC123"))
                .andReturn().getResponse().getStatus();
        int helpStatus = mockMvc.perform(get("/help"))
                .andReturn().getResponse().getStatus();
        assertTrue(joinStatus == 200 || joinStatus == 404, "join 路由不应 401，实际 " + joinStatus);
        assertTrue(helpStatus == 200 || helpStatus == 404, "help 路由不应 401，实际 " + helpStatus);
    }
}
