package com.studyroom.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VectorSearchServiceTests {

    private final VectorSearchService service = new VectorSearchService();

    @Test
    void ranksRelevantChunkFirst() {
        List<String> chunks = List.of(
                "微积分包括微分学和积分学两个部分",
                "今天天气很好适合出门散步",
                "导数描述的是函数的瞬时变化率"
        );
        List<VectorSearchService.ChunkHit> hits = service.searchTop(chunks, "什么是导数？", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunk()).contains("导数");
    }

    @Test
    void handlesChineseOnlyCorpusWithoutError() {
        List<String> chunks = List.of("导数是瞬时变化率。");
        List<VectorSearchService.ChunkHit> hits = service.searchTop(chunks, "什么是导数？", 3);
        assertThat(hits).hasSize(1);
    }

    @Test
    void returnsEmptyForNoChunks() {
        assertThat(service.searchTop(List.of(), "问题", 3)).isEmpty();
    }
}
