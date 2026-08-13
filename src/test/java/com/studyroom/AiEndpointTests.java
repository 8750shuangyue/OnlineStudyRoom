package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 接口测试：用 Mock 替换 ChatClient，不真实调用 DeepSeek，保证测试离线、确定、不花钱。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aimockdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AiEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @TestConfiguration
    static class MockAiConfig {
        @Bean
        @Primary
        ChatClient.Builder mockBuilder() {
            ChatClient.Builder builder = mock(ChatClient.Builder.class);
            ChatClient client = mock(ChatClient.class);
            ChatClient.ChatClientRequestSpec prompt = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
            when(builder.build()).thenReturn(client);
            when(client.prompt()).thenReturn(prompt);
            when(prompt.user(anyString())).thenReturn(prompt);
            when(prompt.call()).thenReturn(call);
            when(call.content()).thenReturn("模拟回答");
            return builder;
        }
    }

    private String register(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void aiEndpointsUseMockedModel() throws Exception {
        // 匿名访问 AI 必须被拒绝（安全收紧）
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isUnauthorized());

        String token = register("alice");

        // 学习计划
        mockMvc.perform(post("/api/ai/study-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"一个月搞定微积分\",\"hoursPerDay\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("模拟回答"));

        // 错题讲解
        String mistakeJson = mockMvc.perform(post("/api/mistakes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"数学\",\"question\":\"求导\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long mistakeId = ((Number) JsonPath.read(mistakeJson, "$.id")).longValue();
        mockMvc.perform(post("/api/ai/mistakes/" + mistakeId + "/explain")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("模拟回答"));

        // 资料问答（先上传资料再提问）
        MockMultipartFile file = new MockMultipartFile("file", "笔记.txt", "text/plain",
                "导数是瞬时变化率。".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/ai/rag")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是导数？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("模拟回答"))
                .andExpect(jsonPath("$.sources[0].name").value("笔记.txt"));

        // 番茄总结（建房间 + 会话满 15 分钟）
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AI 测试房\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();
        String startJson = mockMvc.perform(post("/api/sessions/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + roomId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long sessionId = ((Number) JsonPath.read(startJson, "$.id")).longValue();
        jdbc.update("update study_sessions set started_at = dateadd('MINUTE', -20, started_at) where id = ?",
                sessionId);
        mockMvc.perform(post("/api/sessions/" + sessionId + "/stop")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/sessions/" + sessionId + "/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("模拟回答"));
    }
}
