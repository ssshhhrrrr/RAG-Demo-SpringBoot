package com.ikko.rag_demo.service.impl;

import com.ikko.rag_demo.dto.AskResponseData;
import com.ikko.rag_demo.rag.chunker.TextChunker;
import com.ikko.rag_demo.rag.embedding.VectorEmbedder;
import com.ikko.rag_demo.rag.generator.LlmGenerator;
import com.ikko.rag_demo.rag.parser.FileParser;
import com.ikko.rag_demo.rag.retriever.VectorRetriever;
import com.ikko.rag_demo.service.DocumentAsyncProcessor;
import com.ikko.rag_demo.service.KnowledgeService;
import com.ikko.rag_demo.service.TaskStatusManager;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * 服务实现类
 * @author shenhaoran
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    // 🌟 注入我们刚刚写好的 5 大金刚
    private final FileParser fileParser;
    private final TextChunker textChunker;
    private final VectorEmbedder vectorEmbedder;
    private final VectorRetriever vectorRetriever;
    private final LlmGenerator llmGenerator;
    // 🌟 注入刚写的异步类
    @Autowired
    private DocumentAsyncProcessor asyncProcessor;
    // 依然是注入接口，Spring 会自动找到 CaffeineTaskStatusManagerImpl
    @Autowired
    private TaskStatusManager statusManager;

    public KnowledgeServiceImpl(FileParser fileParser, TextChunker textChunker,
                                VectorEmbedder vectorEmbedder, VectorRetriever vectorRetriever,
                                LlmGenerator llmGenerator) {
        this.fileParser = fileParser;
        this.textChunker = textChunker;
        this.vectorEmbedder = vectorEmbedder;
        this.vectorRetriever = vectorRetriever;
        this.llmGenerator = llmGenerator;
    }

    @Override
    public void processAndStoreDocument(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("上传的文件名不能为空！");
        }

        File dir = new File(uploadDir).getAbsoluteFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File localFile = new File(dir, fileName);
        // 🌟 核心升级：同名文件自动清理与覆盖逻辑
        if (localFile.exists()) {
            System.out.println("⚠️ [更新流程] 发现同名文件 [" + fileName + "]，正在执行覆盖更新...");

            // 1. 抹除记忆：通知 VectorEmbedder 清理 Chroma 里的旧向量
            vectorEmbedder.deleteOldVectorsByFileName(fileName);

            // 2. 斩断现实：删除旧的物理文件
            localFile.delete();
            System.out.println("♻️ [更新流程] 旧文件及旧向量已清理完毕。");
        }

        // 1. 瞬间完成：物理文件存盘
        file.transferTo(localFile);
        System.out.println("💾 [主线程] 文件已火速保存至硬盘");

        // 🌟 登记造册：状态标记为处理中
        statusManager.setStatus(fileName, "PROCESSING");

        // 扔给后台线程 (非阻塞)
        asyncProcessor.executeIngestionTask(localFile, fileName);

        // 3. 立刻结束主线程，前端会立刻收到 Success！
        System.out.println("🚀 [主线程] 任务已推入后台队列，主请求结束响应！");
    }

    @Override
    public AskResponseData askQuestion(String question) {

        // 1. 🔍 柔性探测：获取当前正在解析的文件，不再抛出异常阻断流程！
        List<String> parsingFiles = statusManager.getCurrentlyParsingFiles();
        String noticeMessage = "";
        if (!parsingFiles.isEmpty()) {
            String fileNames = String.join("、", parsingFiles);
            // 拼装一个友好的提示语
            noticeMessage = "💡温馨提示：您上传的文档【" + fileNames + "】仍在后台努力解析中，因此本次回答暂未包含该文档的最新知识哦。\n\n";
            System.out.println("⚠️ [降级响应] 用户发起了提问，但存在未解析完的文件: " + fileNames);
        }

        // 1. 将问题向量化
        dev.langchain4j.data.embedding.Embedding queryVector = vectorEmbedder.embedText(question);

        // 2. 检索相关片段
        List<EmbeddingMatch<TextSegment>> matches = vectorRetriever.search(queryVector, 5, 0.5);

        // 3. 组装上下文和溯源信息
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

        // 4. 调用大模型生成答案
        String aiAnswer = llmGenerator.generateAnswer(question, contextBuilder.toString());
        System.out.println("答案已经生成");

        // 5. 封装返回结果
        AskResponseData result = new AskResponseData();
        // 💡 核心修改点：将温馨提示（如果有的话）拼接到 AI 答案的最前面！
        result.setAnswer(noticeMessage + aiAnswer);
        result.setSources(sources);

        return result;
    }
}