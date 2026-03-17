package com.ikko.rag_demo.service;

/**
 * 任务状态管理器接口
 * 核心设计：隔离具体的存储实现（本地缓存 vs Redis等）
 */
public interface TaskStatusManager {

    /**
     * 更新文件处理状态
     */
    void setStatus(String fileName, String status);

    /**
     * 查询单个文件的状态
     */
    String getStatus(String fileName);

    /**
     * 检查当前是否还有正在处理中的任务
     */
    boolean hasProcessingTasks();
}