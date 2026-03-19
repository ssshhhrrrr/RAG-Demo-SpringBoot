package com.ikko.rag_demo.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikko.rag_demo.service.TaskStatusManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service // 🌟 声明这是一个服务实现类，Spring 会把它注入到需要接口的地方
public class CaffeineTaskStatusManagerImpl implements TaskStatusManager {

    // 使用 Caffeine 构建高性能、防泄漏的本地缓存
    private final Cache<String, String> statusCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    @Override
    public void setStatus(String fileName, String status) {
        statusCache.put(fileName, status);
    }

    @Override
    public String getStatus(String fileName) {
        String status = statusCache.getIfPresent(fileName);
        return status != null ? status : "NOT_FOUND";
    }

    @Override
    public boolean hasProcessingTasks() {
        // 遍历缓存中的值，看是否还有 PROCESSING
        return statusCache.asMap().containsValue("PROCESSING");
    }
    /**
     * 获取当前正在后台解析的所有文件名
     */
    @Override
    public List<String> getCurrentlyParsingFiles() {
        // 遍历 Caffeine 缓存中的所有数据
        return statusCache.asMap().entrySet().stream()
                // 只找状态是 PROCESSING 的
                .filter(entry -> "PROCESSING".equals(entry.getValue()))
                // 把文件名提取出来
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

}