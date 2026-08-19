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
 * 系统公告：管理员可发布，普通用户 403，全员可读；/me 返回 admin 标记。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:announcementdb;DB_CLOSE_DELAY=-1",
        "app.admin.username=annadmin"
})
@AutoConfigureMockMvc
class AnnouncementFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanPublishAndOthersRead() throws Exception {
        String admin = register("annadmin");
        String user = register("annuser");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value("true"));

        mockMvc.perform(post("/api/announcements/admin")
                        .header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/announcements/admin")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"维护公告\",\"content\":\"今晚维护\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("维护公告"));

        mockMvc.perform(get("/api/announcements").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("维护公告"));
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
