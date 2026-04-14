package com.ikko.rag_demo.service;

import java.io.File;

/**
 * 文档异步处理服务接口
 */
public interface DocumentAsyncProcessor {
    
    /**
     * 在后台异步执行文档解析与向量化入库
     *
     * @param localFile         新上传文件的物理文件对象
     * @param fileName          业务文件名
     * @param previousFilePath  当前生效版本的旧文件路径，可为空
     */
    void executeIngestionTask(File localFile, String fileName, String previousFilePath);
    
}
