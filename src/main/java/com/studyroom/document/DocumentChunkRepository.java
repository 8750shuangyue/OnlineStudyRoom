package com.studyroom.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    void deleteByDocumentId(Long documentId);

    long countByDocumentId(Long documentId);

    @Query("""
            select c.document.id, count(c) from DocumentChunk c
            where c.document.id in :ids
            group by c.document.id
            """)
    List<Object[]> countByDocumentIds(@Param("ids") Collection<Long> ids);

    @Query("""
            select c.document.id, c.document.name, c.content
            from DocumentChunk c
            where c.document.user.id = :userId
            order by c.document.createdAt desc, c.chunkIndex
            """)
    List<Object[]> findChunkRowsByUserId(@Param("userId") Long userId);
}
