package com.ikko.rag_demo.rag.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Component
public class VectorEmbedder {
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    // 🌟 新增：Spring 自带的 HTTP 请求工具
    private final RestTemplate restTemplate = new RestTemplate();

    // 🌟 让 Spring 把 AiConfig 里配置好的阿里云大模型塞进来
    public VectorEmbedder(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    // 暴露一个专门把文字转成向量的方法（检索时用）
    public dev.langchain4j.data.embedding.Embedding embedText(String text) {
        return embeddingModel.embed(text).content();
    }

    // 暴露给存入流程的封装方法
    public void ingest(Document document, DocumentSplitter splitter) {

        List<TextSegment> segments = splitter.split(document);

        int batchSize = 10;

        for (int i = 0; i < segments.size(); i += batchSize) {

            List<TextSegment> batch = segments.subList(
                    i,
                    Math.min(i + batchSize, segments.size())
            );

            List<Embedding> embeddings =
                    embeddingModel.embedAll(batch).content();
            System.out.println(embeddings.get(0).vector().length);

            embeddingStore.addAll(embeddings, batch);
        }
    }

//    /**
//     * 根据文件名，调用 Chroma 原生 API 删除旧向量
//     */
    public void deleteOldVectorsByFileName(String fileName) {
    try {
        // 构建过滤器：精准狙击所有元数据 "file_name" 等于当前文件名的切片
        Filter filter = metadataKey("file_name").isEqualTo(fileName);

        // 呼叫 Chroma 执行全量删除
        embeddingStore.removeAll(filter);
        System.out.println("🗑️ [向量清理] 已成功清除旧文件 [" + fileName + "] 的所有历史切片！");
    } catch (Exception e) {
        System.err.println("❌ [向量清理] 清理失败，可能是底层数据库暂不支持此过滤操作: " + e.getMessage());
    }
}

}