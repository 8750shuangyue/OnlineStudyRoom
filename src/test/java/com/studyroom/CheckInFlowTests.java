package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Date;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 每日签到：首次签到、重复签到 409、连续签到 streak 计算。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:checkindb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class CheckInFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void checkInFlow() throws Exception {
        String token = register("checkinuser");

        mockMvc.perform(get("/api/checkins").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedToday").value(false))
                .andExpect(jsonPath("$.streak").value(0));

        mockMvc.perform(post("/api/checkins").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkedToday").value(true))
                .andExpect(jsonPath("$.streak").value(1))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(post("/api/checkins").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void streakContinuesWithYesterday() throws Exception {
        String token = register("streakuser");
        jdbc.update("""
                insert into check_ins (user_id, check_date, created_at)
                select id, ?, current_timestamp from users where username = 'streakuser'
                """, Date.valueOf(LocalDate.now().minusDays(1)));

        mockMvc.perform(post("/api/checkins").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.streak").value(2))
                .andExpect(jsonPath("$.total").value(2));
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
