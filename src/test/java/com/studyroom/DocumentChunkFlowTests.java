package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RAG 预切分：上传时生成 chunk、删除时清理、超大文档拒绝。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chunkdb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class DocumentChunkFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String register(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void uploadCreatesChunksAndDeleteRemovesThem() throws Exception {
        String token = register("chunkalice");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            sb.append("微积分研究变化与累积，导数表示瞬时变化率，积分表示累积量。");
        }
        MockMultipartFile file = new MockMultipartFile("file", "微积分.txt", "text/plain",
                sb.toString().getBytes(StandardCharsets.UTF_8));
        String docJson = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long docId = ((Number) JsonPath.read(docJson, "$.id")).longValue();

        Long chunks = jdbc.queryForObject(
                "select count(*) from document_chunks where document_id = ?", Long.class, docId);
        assertTrue(chunks != null && chunks >= 2, "长文档应预切分为多个 chunk");

        mockMvc.perform(delete("/api/documents/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        Long left = jdbc.queryForObject(
                "select count(*) from document_chunks where document_id = ?", Long.class, docId);
        assertTrue(left == null || left == 0, "删除文档后 chunk 应一并删除");
    }

    @Test
    void oversizedDocumentRejected() throws Exception {
        String token = register("chunkbob");
        String content = "a".repeat(200_001);
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
