# RAG Demo (Spring Boot + Chroma + Redis + LangChain4j)

一个基于 Spring Boot 的知识库问答 Demo，支持：

- 文档上传后异步解析与向量入库
- 同步问答 `/api/knowledge/ask`
- SSE 流式问答 `/api/knowledge/ask/stream`
- 会话记忆（Redis）
- Rerank 精排（默认阿里云 DashScope）

## 技术栈

- Java 17
- Spring Boot 3.2.4
- LangChain4j 0.31.0
- ChromaDB（向量库）
- Redis（会话记忆 + 文档版本路径记录）
- Maven

## 项目结构

```text
src/main/java/com/ikko/rag_demo
├── config          # AI/线程池/向量库/RedisChatMemoryStore 配置
├── controller      # KnowledgeController 接口层
├── dto             # 请求与响应 DTO
├── rag             # parser/chunker/retriever/embedder
├── service         # 业务接口
├── service/impl    # 业务实现（上传、问答、异步处理、rerank）
└── util            # LlamaParse 调用工具
```

## 运行前准备

### 1) 启动 Chroma

```bash
docker compose up -d chromadb
```

默认地址固定为 `http://127.0.0.1:8000`（见 `VectorStoreConfig`）。

### 2) 启动 Redis

`docker-compose.yml` 里 Redis 是注释状态，建议直接本地起一个：

```bash
docker run -d --name rag-redis -p 6379:6379 redis:7.2-alpine
```

### 3) 配置本地私有参数

项目已支持 `application-local.yaml` 覆盖主配置：

1. 复制模板
```bash
cp src/main/resources/application-local.example.yaml src/main/resources/application-local.yaml
```
2. 在 `application-local.yaml` 填入真实 key（该文件已被 `.gitignore` 忽略）

## 启动项目

```bash
./mvnw spring-boot:run
```

服务默认端口：`8080`

## API 文档

统一返回结构：

```json
{
  "status": "success|error",
  "message": "说明",
  "data": {}
}
```

### 1) 上传文档

- `POST /api/knowledge/upload`
- `multipart/form-data`
- 参数：`file`

示例：

```bash
curl -X POST http://127.0.0.1:8080/api/knowledge/upload \
  -F "file=@/path/to/your-doc.txt"
```

说明：

- 上传接口会快速返回，解析和入库在异步线程执行
- 同名文档采用“先入新、后删旧”策略，避免新版本失败时丢失旧数据

### 2) 同步问答

- `POST /api/knowledge/ask`
- `application/json`

请求体：

```json
{
  "sessionId": "user-1",
  "question": "根据知识库描述 Mind One 的外观。"
}
```

示例：

```bash
curl -X POST http://127.0.0.1:8080/api/knowledge/ask \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"user-1","question":"根据知识库描述 Mind One 的外观。"}'
```

### 3) 流式问答（SSE）

- `POST /api/knowledge/ask/stream`
- `Content-Type: application/json`
- `Accept: text/event-stream`

请求体与 `/ask` 相同。

返回为分段事件（`text`/`source`/`error`/`done`）。

## 关键配置说明

主配置文件：`src/main/resources/application.yaml`

- `spring.data.redis.*`：Redis 连接
- `file.upload-dir`：上传文件目录（默认 `./uploads`）
- `ai.aliyun.*`：聊天与 embedding 模型配置
- `ai.rerank.aliyun.*`：阿里云 rerank 配置
- `ai.rerank.siliconflow.*`：硅基流动 rerank（仅配置了 api-key 才启用）
- `ai.llama-parse.*`：PDF 解析配置

## 当前行为与注意点

- 文档处理线程池与流式问答线程池分离（`docAsyncExecutor` / `aiStreamExecutor`）
- 同步问答是串行链路（embedding -> retrieval -> rerank -> LLM），复杂问题响应可能较慢
- LlamaParse 轮询已增加超时保护，避免无限等待
- Chroma 地址目前在代码中固定为 `127.0.0.1:8000`，如需多环境建议后续改为可配置

## 测试

```bash
./mvnw test
```

当前包含基础 `contextLoads` 启动测试。

