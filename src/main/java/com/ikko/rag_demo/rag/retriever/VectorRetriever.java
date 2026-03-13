package com.ikko.rag_demo.rag.retriever;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VectorRetriever {
    private final EmbeddingStore<TextSegment> embeddingStore;

    public VectorRetriever(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    public List<EmbeddingMatch<TextSegment>> search(dev.langchain4j.data.embedding.Embedding queryEmbedding, int maxResults, double minScore) {
        return embeddingStore.findRelevant(queryEmbedding, maxResults, minScore);
    }
}