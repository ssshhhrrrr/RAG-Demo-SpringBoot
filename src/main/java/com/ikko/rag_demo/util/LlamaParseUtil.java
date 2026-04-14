package com.ikko.rag_demo.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Component
public class LlamaParseUtil {
    private static final long POLL_INTERVAL_MILLIS = 3000L;
    private static final long MAX_POLL_DURATION_MILLIS = 3 * 60 * 1000L;

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final String apiKey;
    private final String baseUrl;

    public LlamaParseUtil(
            @Value("${ai.llama-parse.api-key:}") String apiKey,
            @Value("${ai.llama-parse.base-url:https://api.cloud.llamaindex.ai/api/parsing}") String baseUrl
    ) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    /**
     * 将 PDF 文件转换为 Markdown 字符串
     */
    public String parsePdfToMarkdown(File pdfFile) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("缺少配置 ai.llama-parse.api-key");
        }

        System.out.println("🚀 [LlamaParse] 开始上传文件: " + pdfFile.getName());

        // 1. 上传文件获取 Job ID
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", pdfFile.getName(),
                        RequestBody.create(pdfFile, MediaType.parse("application/pdf")))
                .build();

        Request uploadRequest = new Request.Builder()
                .url(baseUrl + "/upload")
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        String jobId;
        try (Response response = client.newCall(uploadRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("上传失败: " + response.body().string());
            }
            JSONObject resJson = JSON.parseObject(response.body().string());
            jobId = resJson.getString("id");
            if (jobId == null || jobId.isBlank()) {
                throw new RuntimeException("LlamaParse 上传成功但未返回任务 ID");
            }
            System.out.println("✅ [LlamaParse] 上传成功，任务ID: " + jobId);
        }

        // 2. 轮询等待解析完成 (PDF 带图片解析较慢，可能需要十几秒)
        long deadline = System.currentTimeMillis() + MAX_POLL_DURATION_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            System.out.println("⏳ [LlamaParse] 正在解析中，请稍候...");
            Thread.sleep(POLL_INTERVAL_MILLIS);

            Request statusRequest = new Request.Builder()
                    .url(baseUrl + "/job/" + jobId)
                    .header("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();

            try (Response statusResponse = client.newCall(statusRequest).execute()) {
                if (!statusResponse.isSuccessful()) {
                    throw new RuntimeException("查询 LlamaParse 状态失败: " + statusResponse.body().string());
                }
                JSONObject statusJson = JSON.parseObject(statusResponse.body().string());
                String status = statusJson.getString("status");
                
                if ("SUCCESS".equals(status)) {
                    System.out.println("🎉 [LlamaParse] 解析完成！正在获取 Markdown 结果...");
                    break;
                } else if ("ERROR".equals(status)) {
                    throw new RuntimeException("LlamaParse 解析出错!");
                }
            }
        }
        if (System.currentTimeMillis() >= deadline) {
            throw new RuntimeException("LlamaParse 解析超时，超过 " + (MAX_POLL_DURATION_MILLIS / 1000) + " 秒仍未完成");
        }

        // 3. 获取最终的 Markdown 内容
        Request resultRequest = new Request.Builder()
                .url(baseUrl + "/job/" + jobId + "/result/markdown")
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response resultResponse = client.newCall(resultRequest).execute()) {
            if (!resultResponse.isSuccessful()) {
                throw new RuntimeException("获取 LlamaParse 结果失败: " + resultResponse.body().string());
            }
            JSONObject resultJson = JSON.parseObject(resultResponse.body().string());
            String markdown = resultJson.getString("markdown");
            if (markdown == null) {
                throw new RuntimeException("LlamaParse 返回结果中缺少 markdown 字段");
            }
            return markdown;
        }
    }
}
