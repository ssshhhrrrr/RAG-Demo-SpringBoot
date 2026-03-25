package com.ikko.rag_demo.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikko.rag_demo.config.RedisChatMemoryStore;
import com.ikko.rag_demo.dto.AskResponseData;
import com.ikko.rag_demo.dto.StreamResponse;
import com.ikko.rag_demo.rag.chunker.TextChunker;
import com.ikko.rag_demo.rag.embedding.VectorEmbedder;
import com.ikko.rag_demo.rag.generator.LlmGenerator;
import com.ikko.rag_demo.rag.parser.FileParser;
import com.ikko.rag_demo.rag.retriever.VectorRetriever;
import com.ikko.rag_demo.service.DocumentAsyncProcessor;
import com.ikko.rag_demo.service.KnowledgeService;
import com.ikko.rag_demo.service.TaskStatusManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库服务实现类 - 增强版
 * @author shenhaoran
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private final FileParser fileParser;
    private final TextChunker textChunker;
    private final VectorEmbedder vectorEmbedder;
    private final VectorRetriever vectorRetriever;
    private final LlmGenerator llmGenerator;

    @Autowired
    private DocumentAsyncProcessor asyncProcessor;
    @Autowired
    private TaskStatusManager statusManager;
    @Autowired
    private StreamingChatLanguageModel streamingLlmGenerator;
    @Autowired
    private ChatLanguageModel syncChatLanguageModel;
    @Autowired
    private RedisChatMemoryStore redisChatMemoryStore;

    // 🌟 注入你在配置类中定义的专属线程池
    @Autowired
    @Qualifier("aiStreamExecutor")
    private Executor aiStreamExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeServiceImpl(FileParser fileParser, TextChunker textChunker,
                                VectorEmbedder vectorEmbedder, VectorRetriever vectorRetriever,
                                LlmGenerator llmGenerator) {
        this.fileParser = fileParser;
        this.textChunker = textChunker;
        this.vectorEmbedder = vectorEmbedder;
        this.vectorRetriever = vectorRetriever;
        this.llmGenerator = llmGenerator;
    }

    private ChatMemory getOrCreateChatMemory(String sessionId) {
        return MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(10)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

    @Override
    public void processAndStoreDocument(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        File dir = new File(uploadDir).getAbsoluteFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File localFile = new File(dir, fileName);
        if (localFile.exists()) {
            vectorEmbedder.deleteOldVectorsByFileName(fileName);
            localFile.delete();
        }
        file.transferTo(localFile);
        statusManager.setStatus(fileName, "PROCESSING");
        asyncProcessor.executeIngestionTask(localFile, fileName);
    }

    /**
     * 同步提问逻辑
     */
    @Override
    public AskResponseData askQuestion(String sessionId, String question) {
        List<String> parsingFiles = statusManager.getCurrentlyParsingFiles();
        String noticeMessage = parsingFiles.isEmpty() ? "" :
                "💡温馨提示：文档【" + String.join("、", parsingFiles) + "】正在解析，暂未包含其最新知识。\n\n";

        dev.langchain4j.data.embedding.Embedding queryVector = vectorEmbedder.embedText(question);
        List<EmbeddingMatch<TextSegment>> matches = vectorRetriever.search(queryVector, 5, 0.5);

        List<AskResponseData.Source> sources = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : matches) {
            AskResponseData.Source source = new AskResponseData.Source();
            source.setChunkId(match.embeddingId());
            source.setDocumentId(match.embedded().metadata().getString("file_name"));
            source.setText(match.embedded().text());
            sources.add(source);
            contextBuilder.append(match.embedded().text()).append("\n\n");
        }

        String finalSessionId = (sessionId == null || sessionId.isEmpty()) ? "default-session" : sessionId;
        ChatMemory chatMemory = getOrCreateChatMemory(finalSessionId);

        List<ChatMessage> tempMessages = new ArrayList<>(chatMemory.messages());
        String enrichedPrompt = "参考资料：\n" + contextBuilder + "\n\n问题：" + question;
        tempMessages.add(UserMessage.from(enrichedPrompt));

        Response<AiMessage> response = syncChatLanguageModel.generate(tempMessages);

        // 存储纯净对话
        chatMemory.add(UserMessage.from(question));
        chatMemory.add(response.content());

        AskResponseData result = new AskResponseData();
        result.setAnswer(noticeMessage + response.content().text());
        result.setSources(sources);
        return result;
    }

    /**
     * 流式提问逻辑 - 深度优化版
     */
    @Override
    public void askQuestionStream(String sessionId, String question, SseEmitter emitter) {
        // 🌟 1. 立即检查解析状态（主线程执行，响应最快）
        List<String> parsingFiles = statusManager.getCurrentlyParsingFiles();
        if (!parsingFiles.isEmpty()) {
            try {
                String tip = "💡温馨提示：文档【" + String.join("、", parsingFiles) + "】仍在努力解析中，本次回答暂未包含其内容哦。";
                emitter.send(objectMapper.writeValueAsString(new StreamResponse("warning", tip)));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return;
            }
        }

        // 🌟 2. 将耗时的检索逻辑放入专属线程池
        aiStreamExecutor.execute(() -> {
            try {
                // 执行向量检索
                dev.langchain4j.data.embedding.Embedding queryVector = vectorEmbedder.embedText(question);
                List<EmbeddingMatch<TextSegment>> matches = vectorRetriever.search(queryVector, 5, 0.5);

                StringBuilder contextBuilder = new StringBuilder();
                StringBuilder sourceTextBuilder = new StringBuilder("\n\n---\n📚 参考资料：\n");
                for (int i = 0; i < matches.size(); i++) {
                    TextSegment segment = matches.get(i).embedded();
                    contextBuilder.append(segment.text()).append("\n\n");
                    sourceTextBuilder.append(i + 1).append(". [").append(segment.metadata().getString("file_name")).append("] ")
                            .append(segment.text()).append("\n");
                }

                // 准备记忆
                String finalSessionId = (sessionId == null || sessionId.isEmpty()) ? "default-session" : sessionId;
                ChatMemory chatMemory = getOrCreateChatMemory(finalSessionId);
                List<ChatMessage> tempMessages = new ArrayList<>(chatMemory.messages());
                tempMessages.add(UserMessage.from("参考资料：\n" + contextBuilder + "\n\n问题：" + question));

                // 🌟 3. 设置原子锁，防止重复写入 Redis
                AtomicBoolean isHandled = new AtomicBoolean(false);

                streamingLlmGenerator.generate(tempMessages, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        try {
                            emitter.send(objectMapper.writeValueAsString(new StreamResponse("text", token)));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        // 🌟 4. 确保逻辑只执行一次（防重核心）
                        System.out.println("🚩 [Debug] onComplete 被调用了！SessionId: " + finalSessionId);
                        if (isHandled.compareAndSet(false, true)) {
                            try {
                                // 持久化纯净对话到 Redis
                                chatMemory.add(UserMessage.from(question));
                                chatMemory.add(response.content());

                                // 发送溯源信息和结束信号
                                emitter.send(objectMapper.writeValueAsString(new StreamResponse("source", sourceTextBuilder.toString())));
                                emitter.send(objectMapper.writeValueAsString(new StreamResponse("done", "FINISHED")));
                                emitter.complete();
                                System.out.println("✅ 流式任务处理完毕并已存入 Redis");
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (isHandled.compareAndSet(false, true)) {
                            try {
                                emitter.send(objectMapper.writeValueAsString(new StreamResponse("error", error.getMessage())));
                            } catch (Exception ignored) {}
                            emitter.completeWithError(error);
                        }
                    }
                });

            } catch (Exception e) {
                System.err.println("❌ 异步处理发生异常：" + e.getMessage());
                emitter.completeWithError(e);
            }
        });
    }
}