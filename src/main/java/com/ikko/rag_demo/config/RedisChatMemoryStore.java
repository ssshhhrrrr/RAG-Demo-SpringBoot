package com.ikko.rag_demo.config; // 或者放在 store 包下

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "rag:chat:memory:";

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate) {

        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + memoryId);
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        // 存入 Redis，设置 7 天过期，防止冗余数据撑爆内存
        redisTemplate.opsForValue().set(KEY_PREFIX + memoryId, json, 7, TimeUnit.DAYS);
    }

    @Override
    public void deleteMessages(Object memoryId) {

        redisTemplate.delete(KEY_PREFIX + memoryId);
    }
}