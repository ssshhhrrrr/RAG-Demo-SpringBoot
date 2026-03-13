package com.ikko.rag_demo.rag.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class VectorEmbedder {
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    // 🌟 新增：Spring 自带的 HTTP 请求工具
    private final RestTemplate restTemplate = new RestTemplate();

    public VectorEmbedder(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    // 暴露一个专门把文字转成向量的方法（检索时用）
    public dev.langchain4j.data.embedding.Embedding embedText(String text) {
        return embeddingModel.embed(text).content();
    }

    // 暴露给存入流程的封装方法
    public void ingest(Document document, DocumentSplitter splitter) {
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(document);
    }

    /**
     * 根据文件名，调用 Chroma 原生 API 删除旧向量
     */
    public void deleteByFileName(String fileName) {
        // ⚠️ 注意：这里的 URL 中必须使用你在 Config 里配置的 collectionName（之前写的是 ai_knowledge_base）
        String chromaUrl = "http://127.0.0.1:8000/api/v1/collections/ai_knowledge_base/delete";

        try {
            // 组装 Chroma 要求的请求体：{"where": {"file_name": "test.txt"}}
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, String> whereClause = new HashMap<>();
            whereClause.put("file_name", fileName);
            requestBody.put("where", whereClause);

            // 发送 POST 请求执行精确删除
            restTemplate.postForEntity(chromaUrl, requestBody, String.class);
            System.out.println("🧹 [清理动作] 成功从 Chroma 中抹除 [" + fileName + "] 的历史记忆。");
        } catch (Exception e) {
            // 这里不抛出异常，因为如果是第一次上传，Chroma 找不到文件报错是正常的
            System.out.println("⚠️ [清理动作] 跳过（可能是首次上传或集合不存在）");
        }
    }

}