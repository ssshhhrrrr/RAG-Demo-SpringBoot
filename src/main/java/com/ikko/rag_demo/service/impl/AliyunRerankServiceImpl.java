package com.ikko.rag_demo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikko.rag_demo.service.RerankService;
import lombok.extern.slf4j.Slf4j;
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
 * 阿里云百炼 (DashScope) GTE-Rerank 模型的精排实现类
 */
@Slf4j
@Service("aliyunRerankService") // 注意这里的 Bean 名字变了
public class AliyunRerankServiceImpl implements RerankService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 阿里云百炼的专属端点
    @Value("${ai.rerank.aliyun.api-url:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}")
    private String apiUrl;

    @Value("${ai.rerank.aliyun.api-key}")
    private String apiKey;

    // 推荐使用阿里云目前最强的 v2 版本
    @Value("${ai.rerank.aliyun.model:gte-rerank-v2}")
    private String modelName;

    @Override
    public List<String> rerank(String query, List<String> rawContexts, int topK) {
        if (rawContexts == null || rawContexts.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (rawContexts.size() <= topK) {
            log.info("粗排结果数量({})小于等于 topK({})，跳过阿里云 Rerank", rawContexts.size(), topK);
            return rawContexts;
        }

        try {
            // 1. 组装请求 Header
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey); // 阿里云同样使用 Bearer Token

            // 2. 组装请求 Body (⚠️ 阿里云特殊的嵌套格式)
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", modelName);
            
            // 数据必须包在 input 对象中
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("documents", rawContexts);
            payload.put("input", input);

            // 参数包在 parameters 对象中
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("return_documents", true);
            parameters.put("top_n", topK);
            payload.put("parameters", parameters);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // 3. 发起外部调用
            log.debug("开始调用阿里云 DashScope Rerank 接口...");
            long startTime = System.currentTimeMillis();
            
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);
            
            log.debug("阿里云调用成功，耗时: {} ms", (System.currentTimeMillis() - startTime));

            // 4. 解析结果 (⚠️ 阿里云返回的数据包在 output.results 里)
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode resultsNode = root.path("output").path("results");

            List<String> finalResults = new ArrayList<>();
            if (resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    // 防御性解析：优先取 API 返回的 text，如果没开启 return_documents，就根据 index 自己去原数组里捞
                    JsonNode documentNode = node.path("document");
                    if (!documentNode.isMissingNode() && documentNode.has("text")) {
                        finalResults.add(documentNode.path("text").asText());
                    } else {
                        int index = node.path("index").asInt();
                        finalResults.add(rawContexts.get(index));
                    }
                }
            }
            return finalResults;

        } catch (Exception e) {
            // 🛡️ 容灾兜底逻辑不变：挂了就返回粗排原序
            log.error("阿里云 Rerank 接口异常，触发降级！异常信息: {}", e.getMessage(), e);
            return rawContexts.subList(0, Math.min(topK, rawContexts.size()));
        }
    }
}