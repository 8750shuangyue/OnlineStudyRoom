package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mistakedb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class MistakeAndExportTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String registerAndGetToken(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void mistakeCrudAndExport() throws Exception {
        String alice = registerAndGetToken("alice");

        // 错题 CRUD
        String mistakeJson = mockMvc.perform(post("/api/mistakes")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"数学\",\"question\":\"求极限 lim(x→0) sinx/x\",\"note\":\"总是搞混\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("数学"))
                .andReturn().getResponse().getContentAsString();
        long mistakeId = ((Number) JsonPath.read(mistakeJson, "$.id")).longValue();

        mockMvc.perform(put("/api/mistakes/" + mistakeId)
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"数学\",\"question\":\"求极限 lim(x→0) sinx/x\",\"note\":\"已理解\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("已理解"));
        mockMvc.perform(get("/api/mistakes")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("求极限 lim(x→0) sinx/x"));
        mockMvc.perform(delete("/api/mistakes/" + mistakeId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());

        // 创建会话后导出 CSV
        String roomJson = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"导出自习室\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomJson, "$.id")).longValue();
        String startJson = mockMvc.perform(post("/api/sessions/start")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + roomId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long sessionId = ((Number) JsonPath.read(startJson, "$.id")).longValue();
        jdbc.update("update study_sessions set started_at = dateadd('MINUTE', -20, started_at) where id = ?",
                sessionId);
        mockMvc.perform(post("/api/sessions/" + sessionId + "/stop")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/export/sessions.csv")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("开始时间,结束时间,房间,时长(分钟)")))
                .andExpect(content().string(containsString("导出自习室")));
    }
}
