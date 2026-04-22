# llm-gateway-sse

## 项目概述
`llm-gateway-sse` 是一个基于 `Spring Boot 3 + WebFlux` 的流式网关服务，对外提供统一的 `SSE` 接口，屏蔽不同大模型供应商调用差异。  
当前已支持 `mock` 上游和 `OpenAI 兼容协议` 上游（可接入阿里百炼兼容接口）。

---

## 主要功能
- 统一流式接口：`POST /v1/chat/stream`（SSE 事件：`meta / delta / done / error`）
- 真实上游接入：支持 OpenAI 兼容 `chat/completions` 流式转发
- Mock 上游：支持正常、慢速、错误场景，便于本地演练
- 请求治理：支持超时、客户端取消感知
- 安全能力：API Key 鉴权（可开关）
- 限流能力：Redis 限流（可开关）
- 成本与记录：token/cost 估算、完成记录落库（PostgreSQL）
- 异步事件：完成后发布 `request.completed` 到 RabbitMQ
- 可观测性：Prometheus 指标、OTel Trace、日志 `traceId/spanId`

---

## 快速开始

### 1) 环境准备
- JDK 21
- Docker Desktop
- PowerShell

### 2) 启动依赖服务
```powershell
cd 项目路径
docker compose up -d postgres redis rabbitmq
```

### 3) 启动应用（真实百炼上游）
```powershell
$env:UPSTREAM_MODE="openai"
$env:OPENAI_BASE_URL="https://dashscope.aliyuncs.com"
$env:OPENAI_CHAT_COMPLETIONS_PATH="/compatible-mode/v1/chat/completions"
$env:OPENAI_API_KEY="你的百炼Key"
$env:OPENAI_DEFAULT_MODEL="qwen-plus"

mvn spring-boot:run
```

### 4) 健康检查
```powershell
Invoke-WebRequest http://localhost:8080/actuator/health
```

---

## 配置说明

### 基础端口（默认）
- 应用：`8080`
- PostgreSQL（宿主机）：`5786`（容器内 `5432`）
- Redis（宿主机）：`6379`
- RabbitMQ AMQP（宿主机）：`5785`（容器内 `5672`）
- RabbitMQ 管理台：`15672`

### 核心配置项
- 上游模式：`UPSTREAM_MODE`（`mock` / `openai`）
- OpenAI 兼容上游：
  - `OPENAI_BASE_URL`
  - `OPENAI_CHAT_COMPLETIONS_PATH`
  - `OPENAI_API_KEY`
  - `OPENAI_DEFAULT_MODEL`
- Redis / PostgreSQL / RabbitMQ 连接参数：见 `src/main/resources/application.yml`
- API Key 鉴权：
  - `llm-gateway.security.api-key.enabled`
  - `API_KEY_PEPPER`
  - `llm-gateway.security.api-key.hashes`
- 限流：
  - `llm-gateway.ratelimit.enabled`
  - `llm-gateway.ratelimit.rpm`
  - `llm-gateway.ratelimit.window-seconds`

> 示例环境变量模板见 `.env.example`

---

## 测试命令与预期

### 1) 准备请求体
```powershell
@'
{"model":"qwen-plus","messages":[{"role":"user","content":"用一句话介绍Spring WebFlux"}],"timeoutMs":30000}
'@ | Set-Content -Path .\req-ok.json -Encoding utf8
```

### 2) 实时流式文本测试（推荐）
```powershell
curl.exe --globoff -N `
  -H "Accept: text/event-stream" `
  -H "Content-Type: application/json" `
  -H "X-Request-Id: req_stream_ok_001" `
  --data-binary "@.\req-ok.json" `
  http://localhost:8080/v1/chat/stream
```
预期：输出顺序为 `meta -> 多个 delta -> done`，`meta.model=qwen-plus`。

### 3) 超时降级测试
```powershell
@'
{"model":"qwen-plus","messages":[{"role":"user","content":"请详细回答"}],"timeoutMs":1}
'@ | Set-Content -Path .\req-timeout.json -Encoding utf8

curl.exe --globoff -N `
  -H "Accept: text/event-stream" `
  -H "Content-Type: application/json" `
  -H "X-Request-Id: req_stream_timeout_001" `
  --data-binary "@.\req-timeout.json" `
  http://localhost:8080/v1/chat/stream
```
预期：`meta -> error`。

### 4) 客户端取消测试
```powershell
curl.exe --globoff -N `
  -H "Accept: text/event-stream" `
  -H "Content-Type: application/json" `
  -H "X-Request-Id: req_stream_cancel_001" `
  --data-binary "@.\req-ok.json" `
  http://localhost:8080/v1/chat/stream
```
手动 `Ctrl+C` 中断。  
预期：应用日志包含 `clientAborted=true`。

### 5) 指标验证
```powershell
Invoke-WebRequest http://localhost:8080/actuator/prometheus | `
  Select-String "gateway_chat_stream_requests_total|gateway_chat_stream_latency_seconds|gateway_auth_failures_total|gateway_rate_limited_total"
```
预期：可查询到对应指标并随请求增长。

### 6) 落库验证
```powershell
docker exec -it llm-gateway-sse-postgres psql -U llm_gateway -d llm_gateway -c "select request_id,model,status,token_estimated,cost_estimated from gateway_request_records order by started_at desc limit 10;"
```
预期：能看到本次请求记录。

### 7) 直连百炼验证（排障用）
```powershell
$body = @{
  model = "qwen-plus"
  messages = @(@{ role = "user"; content = "hi" })
  stream = $false
} | ConvertTo-Json -Depth 5 -Compress

Invoke-RestMethod -Uri "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions" `
  -Method POST `
  -Headers @{ Authorization = "Bearer $env:OPENAI_API_KEY" } `
  -ContentType "application/json" `
  -Body $body
```
预期：返回包含 `choices`，用于确认 Key / 模型 / 网络正常。

---

## 可扩展功能
- 多供应商适配层（OpenAI / Azure OpenAI / Anthropic 等）
- 更细粒度稳态治理（重试白名单、熔断、隔离舱、分层超时）
- 多租户与管理面（租户、API Key 生命周期、配额）
- 语义缓存与检索增强（pgvector）
- CI/CD 与数据库迁移（Flyway/Liquibase）
- 生产级安全与合规（密钥轮转、脱敏、审计）

