package com.ikko.rag_demo.dto;

public class AskRequest {
    // 用于区分不同用户的会话
    private String sessionId;
    private String question;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}