package com.ikko.rag_demo.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikko.rag_demo.service.TaskStatusManager;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

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
}