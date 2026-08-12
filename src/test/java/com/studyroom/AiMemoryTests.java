package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import com.studyroom.ai.AiMemory;
import com.studyroom.ai.AiMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 会话记忆：带记忆聊天会写入历史，清空接口可清除。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aimemorydb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AiMemoryTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiMemoryRepository aiMemoryRepository;

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
    void chatMemoryAccumulatesAndClears() throws Exception {
        String token = register("memuser");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"什么是导数？\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"它的几何意义呢？\"}"))
                .andExpect(status().isOk());

        long chatRows = aiMemoryRepository.findAll().stream()
                .filter(m -> m.getSessionKey().equals("chat"))
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(4, chatRows, "两轮对话应写入 4 条记忆（2 问 2 答）");

        mockMvc.perform(post("/api/ai/clear-memory")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionKey\":\"chat\"}"))
                .andExpect(status().isNoContent());

        long afterClear = aiMemoryRepository.findAll().stream()
                .filter(m -> m.getSessionKey().equals("chat"))
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(0, afterClear, "清空后不应有 chat 记忆");
    }

    @Test
    void ragMemoryAccumulates() throws Exception {
        String token = register("ragmem");
        MockMultipartFile file = new MockMultipartFile(
                "file", "微积分.txt", "text/plain",
                "微积分是研究变化与累积的数学分支。导数表示瞬时变化率。".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/ai/rag")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是导数？\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/rag")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"它表示什么？\"}"))
                .andExpect(status().isOk());

        long ragRows = aiMemoryRepository.findAll().stream()
                .filter(m -> m.getSessionKey().equals("rag"))
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(4, ragRows, "两轮 RAG 问答应写入 4 条记忆");
    }
}
