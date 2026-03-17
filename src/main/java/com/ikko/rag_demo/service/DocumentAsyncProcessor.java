package com.ikko.rag_demo.service;

import java.io.File;

/**
 * 文档异步处理服务接口
 */
public interface DocumentAsyncProcessor {
    
    /**
     * 在后台异步执行文档解析与向量化入库
     *
     * @param localFile 物理文件对象
     * @param fileName  文件名
     */
    void executeIngestionTask(File localFile, String fileName);
    
}