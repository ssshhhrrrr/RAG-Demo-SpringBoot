package com.ikko.rag_demo.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 可以在你的 Service 层或 DTO 包下建一个对象
@Data                // 自动生成 Getter, Setter, toString 等
@AllArgsConstructor  // 生成全参构造函数
@NoArgsConstructor   // 生成无参构造函数
public class StreamResponse {
    private String type;    // 标识数据类型： "text" (正文), "source" (参考资料), "error" (报错), "done" (结束)
    private Object content; // 具体内容


}