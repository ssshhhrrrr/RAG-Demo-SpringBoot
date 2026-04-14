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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * @author shenhaoran
 */
@Component
public class VectorEmbedder {
    @Autowired
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private final EmbeddingModel embeddingModel;

    // 🌟 注入我们现成的 Redis 工具
    @Autowired
    private StringRedisTemplate redisTemplate;

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

    public void deleteOldVectorsByFilePath(String filePath) {
        try {
            Filter filter = metadataKey("file_path").isEqualTo(filePath);
            embeddingStore.removeAll(filter);
            System.out.println("🗑️ [向量清理] 已成功清除旧路径 [" + filePath + "] 的历史切片！");
        } catch (Exception e) {
            System.err.println("❌ [向量清理] 按文件路径清理失败: " + e.getMessage());
        }
    }

    /**
     * 🌟 终极前置防重版：利用 Redis 拦截重复切片
     */
    public void embedAndStoreWithDeduplication(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }

        // 专门用来装“真正没见过的新切片”的篮子
        List<TextSegment> uniqueSegments = new ArrayList<>();

        for (TextSegment segment : segments) {
            String rawText = segment.text();
            // 1. 计算文本切片的 MD5 指纹
            String md5Hash = DigestUtils.md5DigestAsHex(rawText.getBytes(StandardCharsets.UTF_8));

            // 2. 拼接 Redis Key (加上你的业务前缀，防冲突)
            String redisKey = "rag:chunk:md5:" + md5Hash;

            // 3. 🌟 降维打击：利用 Redis 的 SETNX (Set If Not Exists) 命令实现极速判断
            // 如果 Key 不存在，存入并返回 true；如果 Key 已存在，什么都不做并返回 false
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1");

            if (Boolean.TRUE.equals(isNew)) {
                // 是新切片！放进篮子里准备处理
                uniqueSegments.add(segment);
            } else {
                // 是重复切片！直接抛弃，连 Embedding 的钱都省了！
                System.out.println("♻️ [拦截] 发现重复切片，已过滤！MD5: " + md5Hash);
            }
        }

        // 如果过滤完之后，发现全是重复的，直接收工回家
        if (uniqueSegments.isEmpty()) {
            System.out.println("⚠️ [跳过] 所有切片均已存在，无需请求大模型和入库！");
            return;
        }

        // 4. 🌟 仅仅对“真正全新的切片”请求大模型生成向量（极其省钱、极速！）
        List<Embedding> embeddings = embeddingModel.embedAll(uniqueSegments).content();

        // 5. 调用官方最原生的方法，毫无阻碍地存入 ChromaDB
        embeddingStore.addAll(embeddings, uniqueSegments);

        System.out.println("✅ [成功] 已过滤重复数据，将 " + uniqueSegments.size() + " 个全新切片存入向量库！");
    }
}
