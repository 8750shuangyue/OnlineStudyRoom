package com.studyroom.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 老数据迁移：为尚未切分的历史文档补齐 document_chunks（幂等）。
 */
@Component
public class DocumentChunkBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkBackfill.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    public DocumentChunkBackfill(DocumentRepository documentRepository,
                                 DocumentChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Document> missing = documentRepository.findDocumentsWithoutChunks();
        if (missing.isEmpty()) {
            return;
        }
        List<DocumentChunk> all = new ArrayList<>();
        for (Document document : missing) {
            all.addAll(DocumentChunking.chunk(document));
        }
        chunkRepository.saveAll(all);
        log.info("DocumentChunkBackfill: 为 {} 份历史文档补齐了切分数据", missing.size());
    }
}
