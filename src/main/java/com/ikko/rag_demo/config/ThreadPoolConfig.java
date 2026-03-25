package com.ikko.rag_demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
@EnableAsync // 🌟 开启 Spring 异步支持，否则 @Async 不会生效
@Configuration
public class ThreadPoolConfig {

    /**
     * 专门用于处理 AI 流式问答的线程池
     */
    @Bean("aiStreamExecutor")
    public Executor aiStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1. 核心线程数：日常常驻的线程数量（看你服务器配置，一般设为 CPU 核心数 * 2）
        executor.setCorePoolSize(10); 
        // 2. 最大线程数：流量洪峰时最多允许多少个线程同时跑
        executor.setMaxPoolSize(50); 
        // 3. 队列容量：当核心线程都在忙时，新来的请求先放进队列排队
        executor.setQueueCapacity(100); 
        // 4. 线程前缀名：极大地提升日志排查效率
        executor.setThreadNamePrefix("AI-Stream-Thread-"); 
        // 5. 拒绝策略：如果连队列都满了，直接抛出异常，让前端提示“当前咨询人数过多，请稍后再试”
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); 
        
        executor.initialize();
        return executor;
    }

    /**
     * 2. 专门用于处理文档解析与向量化的线程池 (CPU 密集型)
     */
    @Bean("docAsyncExecutor")
    public Executor docAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // CPU 密集型任务，核心线程数不需要太大（比如设为 CPU 核心数即可，这里假设为 4）
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        // 允许排队的解析任务多一点
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("Doc-Parse-");
        // 🌟 拒绝策略极度关键：CallerRunsPolicy
        // 如果几百个人同时传文件，队列满了，就不允许新的异步线程去接活了，
        // 退回给当前交任务的 Tomcat 主线程自己去慢吞吞地解析，起到天然的限流保护作用！
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}