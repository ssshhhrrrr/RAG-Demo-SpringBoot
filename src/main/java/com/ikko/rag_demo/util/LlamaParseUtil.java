package com.ikko.rag_demo.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class LlamaParseUtil {

    // 🌟 把这里换成你在 LlamaCloud 申请的 API Key
    private static final String API_KEY = "llx-xtnjO2YnWPp29jZqRWj9kjZKzvZxeufS6YmnlbG9JEKDOw6u";
    private static final String BASE_URL = "https://api.cloud.llamaindex.ai/api/parsing";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 将 PDF 文件转换为 Markdown 字符串
     */
    public static String parsePdfToMarkdown(File pdfFile) throws Exception {
        System.out.println("🚀 [LlamaParse] 开始上传文件: " + pdfFile.getName());

        // 1. 上传文件获取 Job ID
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", pdfFile.getName(),
                        RequestBody.create(pdfFile, MediaType.parse("application/pdf")))
                .build();

        Request uploadRequest = new Request.Builder()
                .url(BASE_URL + "/upload")
                .header("Authorization", "Bearer " + API_KEY)
                .post(requestBody)
                .build();

        String jobId;
        try (Response response = client.newCall(uploadRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("上传失败: " + response.body().string());
            }
            JSONObject resJson = JSON.parseObject(response.body().string());
            jobId = resJson.getString("id");
            System.out.println("✅ [LlamaParse] 上传成功，任务ID: " + jobId);
        }

        // 2. 轮询等待解析完成 (PDF 带图片解析较慢，可能需要十几秒)
        while (true) {
            System.out.println("⏳ [LlamaParse] 正在解析中，请稍候...");
            // 每 3 秒查一次
            Thread.sleep(3000);

            Request statusRequest = new Request.Builder()
                    .url(BASE_URL + "/job/" + jobId)
                    .header("Authorization", "Bearer " + API_KEY)
                    .get()
                    .build();

            try (Response statusResponse = client.newCall(statusRequest).execute()) {
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

        // 3. 获取最终的 Markdown 内容
        Request resultRequest = new Request.Builder()
                .url(BASE_URL + "/job/" + jobId + "/result/markdown")
                .header("Authorization", "Bearer " + API_KEY)
                .get()
                .build();

        try (Response resultResponse = client.newCall(resultRequest).execute()) {
            JSONObject resultJson = JSON.parseObject(resultResponse.body().string());
            return resultJson.getString("markdown");
        }
    }
}