package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段二功能测试：错题间隔重复、笔记分类/搜索/导出、排行榜新维度。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:phase2db;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class Phase2FlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void mistakeReviewFlow() throws Exception {
        String token = register("reviewuser");
        String created = mockMvc.perform(post("/api/mistakes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"数学\",\"question\":\"求导公式\",\"note\":\"忘了链式法则\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewStatus").value("NEW"))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andReturn().getResponse().getContentAsString();
        long mistakeId = ((Number) JsonPath.read(created, "$.id")).longValue();

        // 新建错题立即到期
        mockMvc.perform(get("/api/mistakes/review-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/mistakes/reviews")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("求导公式"));

        // 掌握后下次复习安排到未来，今日待复习归零
        String reviewed = mockMvc.perform(post("/api/mistakes/" + mistakeId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mastered\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.reviewStatus").value("LEARNING"))
                .andReturn().getResponse().getContentAsString();
        String nextReview = JsonPath.read(reviewed, "$.nextReviewAt");
        org.junit.jupiter.api.Assertions.assertNotNull(nextReview);

        mockMvc.perform(get("/api/mistakes/review-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void notesCategorySearchAndExport() throws Exception {
        String token = register("noteuser");
        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"微积分笔记\",\"category\":\"数学\","
                                + "\"tags\":[\"导数\",\"积分\"],\"content\":\"# 导数\\n链式法则\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("数学"))
                .andExpect(jsonPath("$.tags[0]").value("导数"));

        mockMvc.perform(get("/api/notes?search=链式")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("微积分笔记"));
        mockMvc.perform(get("/api/notes?category=数学")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("微积分笔记"));
        mockMvc.perform(get("/api/notes?search=不存在的词")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/notes/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("数学"));

        mockMvc.perform(get("/api/notes/export.md")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("微积分笔记")))
                .andExpect(content().string(containsString("链式法则")));
    }

    @Test
    void leaderboardMetrics() throws Exception {
        String token = register("leaderuser");
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"排行房间\"}"))
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

        mockMvc.perform(get("/api/leaderboard/global?period=all&metric=sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("leaderuser"))
                .andExpect(jsonPath("$[0].value").value(1))
                .andExpect(jsonPath("$[0].unit").value("次"));

        mockMvc.perform(get("/api/leaderboard/global?period=all&metric=streak")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("leaderuser"))
                .andExpect(jsonPath("$[0].value").value(1))
                .andExpect(jsonPath("$[0].unit").value("天"));
    }
}
