package com.studyroom.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    @Query("""
            select d from Document d
            where not exists (select 1 from DocumentChunk c where c.document = d)
            """)
    List<Document> findDocumentsWithoutChunks();
}
