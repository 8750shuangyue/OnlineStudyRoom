package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notedb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class NotesAndRagTests {

    @Autowired
    private MockMvc mockMvc;

    private String registerAndGetToken(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void notesAndDocumentUploadFlow() throws Exception {
        String alice = registerAndGetToken("alice");

        // 笔记 CRUD
        String noteJson = mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"今天要复习微积分\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("今天要复习微积分"))
                .andReturn().getResponse().getContentAsString();
        long noteId = ((Number) JsonPath.read(noteJson, "$.id")).longValue();

        mockMvc.perform(put("/api/notes/" + noteId)
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"微积分复习完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("微积分复习完成"));
        mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("微积分复习完成"));
        mockMvc.perform(delete("/api/notes/" + noteId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());

        // 未上传资料时 RAG 返回 400（不实际调用模型）
        mockMvc.perform(post("/api/ai/rag")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"什么是导数？\"}"))
                .andExpect(status().isBadRequest());

        // 资料上传
        MockMultipartFile file = new MockMultipartFile(
                "file", "微积分笔记.txt", "text/plain",
                "微积分是研究变化与累积的数学分支。导数表示瞬时变化率。".getBytes(StandardCharsets.UTF_8));
        String docJson = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("微积分笔记.txt"))
                .andReturn().getResponse().getContentAsString();
        long docId = ((Number) JsonPath.read(docJson, "$.id")).longValue();

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("微积分笔记.txt"));

        // 删除资料
        mockMvc.perform(delete("/api/documents/" + docId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());
    }
}
