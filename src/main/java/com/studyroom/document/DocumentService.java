package com.studyroom.document;

import com.studyroom.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

@Service
public class DocumentService {

    private static final long MAX_BYTES = 5_000_000;

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional
    public DocumentResponse upload(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能超过 5MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件读取失败");
        }
        String content = extractText(file.getOriginalFilename(), bytes);
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件内容为空");
        }
        Document document = new Document();
        document.setUser(user);
        document.setName(file.getOriginalFilename() == null ? "资料.txt" : file.getOriginalFilename());
        document.setCategory(null);
        document.setContent(content);
        document.setCreatedAt(LocalDateTime.now());
        return toResponse(documentRepository.save(document));
    }

    private String extractText(String fileName, byte[] bytes) {
        String lower = (fileName == null ? "" : fileName.toLowerCase());
        try {
            if (lower.endsWith(".pdf")) {
                try (PDDocument document = Loader.loadPDF(bytes)) {
                    return new PDFTextStripper().getText(document);
                }
            }
            if (lower.endsWith(".docx")) {
                StringBuilder sb = new StringBuilder();
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        sb.append(paragraph.getText()).append('\n');
                    }
                    for (XWPFTable table : document.getTables()) {
                        for (XWPFTableRow row : table.getRows()) {
                            for (XWPFTableCell cell : row.getTableCells()) {
                                sb.append(cell.getText()).append('\n');
                            }
                        }
                    }
                }
                return sb.toString();
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件解析失败，请确认格式正确");
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(User user) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> categories(User user) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(Document::getCategory)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public DocumentResponse update(User user, Long documentId, DocumentUpdateRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在"));
        if (!document.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能修改自己的资料");
        }
        document.setName(request.name().trim());
        document.setCategory(trimToNull(request.category()));
        return toResponse(documentRepository.save(document));
    }

    @Transactional
    public void delete(User user, Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在"));
        if (!document.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能删除自己的资料");
        }
        documentRepository.delete(document);
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(document.getId(), document.getName(), document.getCategory(),
                document.getContent().length(), document.getCreatedAt());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
