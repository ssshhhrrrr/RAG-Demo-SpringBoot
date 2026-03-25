package com.ikko.rag_demo.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 大模型核心配置类 (全线采用 OpenAI 兼容协议接入阿里云)
 */
@Configuration
public class AiConfig {

    @Value("${ai.aliyun.api-key}")
    private String apiKey;

    @Value("${ai.aliyun.base-url}")
    private String baseUrl;

    @Value("${ai.aliyun.model-name}")
    private String modelName;

    // 1. 同步对话模型 (备用)
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.3) // 设低一点，让回答更严谨，防幻觉
                .build();
    }

    // 2. 向量检索模型 (查字典专用)
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName("text-embedding-v3")
                .build();
    }

    // 🌟 3. 新增：流式对话模型 (打字机效果专用)
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl) // 同样注入阿里云的兼容地址
                .apiKey(apiKey)
                .modelName(modelName) // 直接复用你 application.yml 里配置的模型名 (比如 qwen-plus)
                .temperature(0.3) // 同样控制一下严谨度
                .build();
    }
}