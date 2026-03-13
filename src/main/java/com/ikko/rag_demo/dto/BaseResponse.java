package com.ikko.rag_demo.dto;

import lombok.Data;

/**
 * 统一 API 响应封装类
 * @author shenhaoran
 */
@Data // 借助 lombok 自动生成 get/set 方法
public class BaseResponse<T> {
    private String status;
    private String message;
    private T data;

    // 成功时的快捷返回方法
    public static <T> BaseResponse<T> success(String message, T data) {
        BaseResponse<T> response = new BaseResponse<>();
        response.setStatus("success");
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    // 失败时的快捷返回方法
    public static <T> BaseResponse<T> error(String message) {
        BaseResponse<T> response = new BaseResponse<>();
        response.setStatus("error");
        response.setMessage(message);
        return response;
    }
}