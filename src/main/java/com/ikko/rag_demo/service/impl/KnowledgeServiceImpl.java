package com.ikko.rag_demo.service.impl;

import com.ikko.rag_demo.dto.AskResponseData;
import com.ikko.rag_demo.rag.chunker.TextChunker;
import com.ikko.rag_demo.rag.embedding.VectorEmbedder;
import com.ikko.rag_demo.rag.generator.LlmGenerator;
import com.ikko.rag_demo.rag.parser.FileParser;
import com.ikko.rag_demo.rag.retriever.VectorRetriever;
import com.ikko.rag_demo.service.KnowledgeService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
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
        // 严密的空值与格式校验
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("上传的文件名不能为空！");
        }

        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File localFile = new File(dir, fileName);


        if (localFile.exists()) {
            throw new RuntimeException("知识库中已存在名为 [" + fileName + "] 的文件，请勿重复上传。");
            //实际生产中要先删除
            // A. 斩断现实：删除旧的物理文件
//            localFile.delete();
//
//            // B. 抹除记忆：通知 VectorEmbedder 清理 Chroma 里的旧向量
//            vectorEmbedder.deleteByFileName(fileName);
        }

        file.transferTo(localFile);
        System.out.println("💾 [步骤1] 新版本文件已保存至: " + localFile.getAbsolutePath());


        // 🌟 核心主干逻辑：就像流水线一样清晰
        // 1. 解析
        Document document = fileParser.parseToDocument(localFile, fileName);
        // 2. 切片 & 3. 向量化存入
        vectorEmbedder.ingest(document, textChunker.getSplitter());
        System.out.println("✅ [步骤2] 新版本文件解析入库成功！");
    }

    @Override
    public AskResponseData askQuestion(String question) {
        // 1. 将问题向量化
        dev.langchain4j.data.embedding.Embedding queryVector = vectorEmbedder.embedText(question);

        // 2. 检索相关片段
        List<EmbeddingMatch<TextSegment>> matches = vectorRetriever.search(queryVector, 3, 0.6);

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
        result.setAnswer(aiAnswer);
        result.setSources(sources);
        return result;
    }
}