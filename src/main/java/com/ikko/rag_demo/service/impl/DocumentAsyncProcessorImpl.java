package com.ikko.rag_demo.service.impl;

import com.ikko.rag_demo.rag.chunker.TextChunker;
import com.ikko.rag_demo.rag.embedding.VectorEmbedder;
import com.ikko.rag_demo.rag.parser.FileParser;
import com.ikko.rag_demo.service.DocumentAsyncProcessor;
import com.ikko.rag_demo.service.TaskStatusManager;
import com.ikko.rag_demo.util.LlamaParseUtil;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

@Service // 🌟 声明这是一个服务实现类
public class DocumentAsyncProcessorImpl implements DocumentAsyncProcessor {

    private final FileParser fileParser;
    private final TextChunker textChunker;
    private final VectorEmbedder vectorEmbedder;
    // 🌟 注入状态管家
    @Autowired
    private TaskStatusManager statusManager;

    public DocumentAsyncProcessorImpl(FileParser fileParser, TextChunker textChunker, VectorEmbedder vectorEmbedder) {
        this.fileParser = fileParser;
        this.textChunker = textChunker;
        this.vectorEmbedder = vectorEmbedder;
    }

    @Override
    // 核心：明确把重活扔进干重活的池子
    @Async("docAsyncExecutor")
    public void executeIngestionTask(File localFile, String fileName) {
        System.out.println("🧵 [异步线程启动] 开始后台处理文件: " + fileName);
        try {
            Document document;
            
            // 1. 智能分流解析
            if (fileName.toLowerCase().endsWith(".pdf")) {
                System.out.println("🤖 [异步线程] 呼叫 LlamaParse 视觉大模型...");
                String markdownContent = LlamaParseUtil.parsePdfToMarkdown(localFile);
                document = Document.from(markdownContent, Metadata.from("file_name", fileName));
            } else {
                System.out.println("📄 [异步线程] 使用基础 FileParser 解析...");
                document = fileParser.parseToDocument(localFile, fileName);
            }

            // 2. 切片并分批入库
            vectorEmbedder.ingest(document, textChunker.getSplitter());
            System.out.println("🎉 [异步线程结束] 文件 [" + fileName + "] 完美解析并入库！");
            // 🌟 任务圆满完成，状态改为 SUCCESS
            statusManager.setStatus(fileName, "SUCCESS");

        } catch (Exception e) {
            System.err.println("❌ [异步线程异常] 文件处理失败: " + e.getMessage());
            // 🌟 任务失败，状态改为 FAILED
            statusManager.setStatus(fileName, "FAILED");
            e.printStackTrace();
        }
    }
}