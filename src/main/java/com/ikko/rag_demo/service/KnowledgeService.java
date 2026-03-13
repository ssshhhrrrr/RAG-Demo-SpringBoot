package com.ikko.rag_demo.service;

import com.ikko.rag_demo.dto.AskResponseData;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库核心业务接口
 * @author shenhaoran
 */
public interface KnowledgeService {

    /**
     * 处理并存储文档全流程
     * @param file 用户上传的文档
     * @throws Exception 解析或存储异常
     */
    void processAndStoreDocument(MultipartFile file) throws Exception;
    // 记得导入刚才写的 AskResponseData
    AskResponseData askQuestion(String question);

}