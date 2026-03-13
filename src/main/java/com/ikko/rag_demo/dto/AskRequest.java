package com.ikko.rag_demo.dto;

import lombok.Data;

/**
 * 接收前端提问的请求体
 */
@Data
public class AskRequest {
    
    // 用户提出的问题
    private String question;
    
}