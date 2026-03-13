package com.ikko.rag_demo.controller;

import com.ikko.rag_demo.dto.AskRequest;
import com.ikko.rag_demo.dto.AskResponseData;
import com.ikko.rag_demo.dto.BaseResponse; // 🌟 引入刚建的 DTO
import com.ikko.rag_demo.service.KnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author shenhaoran
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/upload")
    public ResponseEntity<BaseResponse<Void>> uploadDocument(@RequestParam("file") MultipartFile file) {
        // 1. 校验空文件
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(BaseResponse.error("上传的文件不能为空"));
        }

        try {
            String fileName = file.getOriginalFilename();
            System.out.println("✅ 成功接收到前端传来的文件: " + fileName);

            // 2. 调用 Service
            knowledgeService.processAndStoreDocument(file);

            // 3. 优雅返回
            return ResponseEntity.ok(BaseResponse.success("文件 [" + fileName + "] 上传并处理成功！", null));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(BaseResponse.error("文件处理失败: " + e.getMessage()));
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<BaseResponse<AskResponseData>> askQuestion(@RequestBody AskRequest request) {
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(BaseResponse.error("问题不能为空"));
        }

        try {
            AskResponseData responseData = knowledgeService.askQuestion(request.getQuestion());
            return ResponseEntity.ok(BaseResponse.success("回答生成成功", responseData));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(BaseResponse.error("问答生成失败: " + e.getMessage()));
        }
    }

}