package com.ikko.rag_demo.dto;

import lombok.Data;
import java.util.List;

/**
 * AI 问答返回结果的封装类
 */
@Data // 使用 Lombok 自动生成 getter/setter
public class AskResponseData {
    
    // AI 生成的具体回答文本
    private String answer;           
    
    // AI 回答时参考的文档切片列表（溯源信息）
    private List<Source> sources;    

    /**
     * 内部类：表示引用的具体片段来源
     */
    @Data
    public static class Source {
        private String documentId;   // 文档 ID (对应 5.8 需求，目前使用 file_name)
        private String chunkId;      // 切片 ID (对应 5.8 需求，Chroma 自动生成的 UUID)
        private String text;         // 切片的具体文本内容
    }
}