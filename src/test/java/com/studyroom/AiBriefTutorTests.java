package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import com.studyroom.notification.Notification;
import com.studyroom.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批次二收尾：每日简报 / 房间助教 / 周度报告。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:briefdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AiBriefTutorTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

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
            when(call.content()).thenReturn("模拟简报");
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
    void dailyBriefCreatesNotification() throws Exception {
        String token = register("briefuser");
        mockMvc.perform(post("/api/ai/daily-brief")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brief").value("模拟简报"));

        List<Notification> notifications = notificationRepository.findAll();
        org.junit.jupiter.api.Assertions.assertTrue(
                notifications.stream().anyMatch(n -> "DAILY_BRIEF".equals(n.getType())),
                "应生成一条 DAILY_BRIEF 通知");
    }

    @Test
    void roomTutorAnswersMembers() throws Exception {
        String token = register("tutoruser");
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"助教房间\",\"aiTutorEnabled\":true,"
                                + "\"tutorPersona\":\"耐心的数学助教\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();

        mockMvc.perform(post("/api/rooms/" + roomId + "/tutor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"什么是导数？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("模拟简报"));
    }

    @Test
    void weeklyReportGenerates() throws Exception {
        String token = register("weeklyuser");
        mockMvc.perform(post("/api/ai/weekly-report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report").value("模拟简报"));
    }
}
