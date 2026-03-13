package com.ikko.rag_demo.rag.generator;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

@Component
public class LlmGenerator {
    private final ChatLanguageModel chatLanguageModel;

    public LlmGenerator(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public String generateAnswer(String question, String context) {
        String prompt = "你是一个专业、严谨的知识库 AI 助手。\n" +
                "请严格根据下方【参考资料】中提供的信息来回答用户的问题。\n" +
                "如果你在【参考资料】中找不到答案，请直接回答“抱歉，知识库中未找到相关信息”。\n\n" +
                "【参考资料】:\n" + context + "\n\n" +
                "【用户问题】: " + question;
        
        System.out.println("🤖 正在呼叫大模型思考中...");
        return chatLanguageModel.generate(prompt);
    }
}