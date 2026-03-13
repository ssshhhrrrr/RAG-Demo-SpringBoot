package com.ikko.rag_demo.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 大模型核心配置类
 */
@Configuration
public class AiConfig {

    // 从 application.yml 中动态读取配置
    @Value("${ai.aliyun.api-key}")
    private String apiKey;

    @Value("${ai.aliyun.base-url}")
    private String baseUrl;

    @Value("${ai.aliyun.model-name}")
    private String modelName;

    /**
     * 注册 ChatLanguageModel Bean，供 generator 模块使用
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.3) // 设低一点，让回答更严谨，更贴合知识库原文
                .build();
    }
}