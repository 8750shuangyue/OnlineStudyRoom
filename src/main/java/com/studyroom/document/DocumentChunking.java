package com.studyroom.document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档切分：上传时预切分入库，RAG 提问时只查 chunk，避免每次全量重建。
 */
public final class DocumentChunking {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_STEP = 400;

    private DocumentChunking() {
    }

    public static List<DocumentChunk> chunk(Document document) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String text = document.getContent() == null ? "" : document.getContent();
        int index = 0;
        for (int i = 0; i < text.length(); i += CHUNK_STEP) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(index++);
            chunk.setContent(text.substring(i, Math.min(text.length(), i + CHUNK_SIZE)));
            chunk.setCreatedAt(LocalDateTime.now());
            chunks.add(chunk);
        }
        return chunks;
    }
}
