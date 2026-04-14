package com.ikko.rag_demo.service;

import java.util.List;

/**
 * RAG 检索链路：重排序（Rerank）服务接口
 */
public interface RerankService {

    /**
     * 对粗排召回的文本片段进行深度语义重排序
     *
     * @param query       用户的原始提问
     * @param rawContexts 粗排阶段（如 Chroma）召回的原始文本列表
     * @param topK        最终需要截取保留的 Top-N 数量
     * @return 精排后并按相关性分数降序排列的文本列表
     */
    List<String> rerank(String query, List<String> rawContexts, int topK);
}