package com.ikko.rag_demo.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // 连接到本地运行的 Chroma 向量数据库
        return ChromaEmbeddingStore.builder()
                // Chroma 服务的默认地址
                .baseUrl("http://127.0.0.1:8000")
                // 知识库集合的名称
                .collectionName("ai_knowledge_base")
                .build();
    }
}
