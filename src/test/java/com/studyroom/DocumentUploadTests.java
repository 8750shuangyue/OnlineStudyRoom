package com.studyroom;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文档上传测试：验证 PDF / DOCX 解析以及损坏文件被拒绝。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:docuploaddb;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class DocumentUploadTests {

    @Autowired
    private MockMvc mockMvc;

    private String register(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void uploadsPdfAndExtractsText() throws Exception {
        String token = register("pdfuser");
        MockMultipartFile pdf = new MockMultipartFile("file", "notes.pdf", "application/pdf", buildPdf());
        mockMvc.perform(multipart("/api/documents")
                        .file(pdf)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.charCount").value(Matchers.greaterThan(0)));
    }

    @Test
    void uploadsDocxAndExtractsText() throws Exception {
        String token = register("docxuser");
        MockMultipartFile docx = new MockMultipartFile("file", "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", buildDocx());
        mockMvc.perform(multipart("/api/documents")
                        .file(docx)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.charCount").value(Matchers.greaterThan(0)));
    }

    @Test
    void rejectsCorruptPdfWith400() throws Exception {
        String token = register("badpdf");
        MockMultipartFile bad = new MockMultipartFile("file", "broken.pdf", "application/pdf",
                "not a pdf at all".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/documents")
                        .file(bad)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private byte[] buildPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Derivative is instantaneous rate of change.");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("微积分研究变化与累积");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }
}
