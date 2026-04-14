package com.ikko.rag_demo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikko.rag_demo.service.RerankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 硅基流动 BGE-Reranker 模型的精排实现类
 */
@Slf4j
@Service("siliconFlowRerankService")
@ConditionalOnExpression("'${ai.rerank.siliconflow.api-key:}' != ''")
public class SiliconFlowRerankServiceImpl implements RerankService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 将这些配置提取到 application.yml 中，避免硬编码
    @Value("${ai.rerank.siliconflow.api-url:https://api.siliconflow.cn/v1/rerank}")
    private String apiUrl;

    @Value("${ai.rerank.siliconflow.api-key}")
    private String apiKey;

    @Value("${ai.rerank.siliconflow.model:BAAI/bge-reranker-v2-m3}")
    private String modelName;

    @Override
    public List<String> rerank(String query, List<String> rawContexts, int topK) {
        // 边界条件防御
        if (rawContexts == null || rawContexts.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 如果粗排数量比要求保留的还少，直接返回，节约一次 API 请求成本
        if (rawContexts.size() <= topK) {
            log.info("粗排结果数量({})小于等于 topK({})，跳过 Rerank", rawContexts.size(), topK);
            return rawContexts;
        }

        try {
            // 1. 组装请求 Header
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // 2. 组装请求 Body
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", modelName);
            payload.put("query", query);
            payload.put("texts", rawContexts);
            payload.put("top_n", topK);
            payload.put("return_documents", true);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // 3. 发起外部调用
            log.debug("开始调用 Rerank 接口，模型: {}, 输入片段数: {}", modelName, rawContexts.size());
            long startTime = System.currentTimeMillis();
            
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);
            
            log.debug("Rerank 接口调用成功，耗时: {} ms", (System.currentTimeMillis() - startTime));

            // 4. 解析结果 (由于 API 默认已按分数降序，只需顺序取出)
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode resultsNode = root.path("results");

            List<String> finalResults = new ArrayList<>();
            if (resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    JsonNode documentNode = node.path("document");
                    if (!documentNode.isMissingNode()) {
                        finalResults.add(documentNode.path("text").asText());
                    } else {
                        int index = node.path("index").asInt();
                        finalResults.add(rawContexts.get(index));
                    }
                }
            }
            return finalResults;

        } catch (Exception e) {
            // 🛡️ 容灾兜底逻辑：记录 ERROR 日志，并返回原始数据的截断版本
            log.error("Rerank 接口调用失败，触发降级保护策略。异常信息: {}", e.getMessage(), e);
            return rawContexts.subList(0, Math.min(topK, rawContexts.size()));
        }
    }
}
