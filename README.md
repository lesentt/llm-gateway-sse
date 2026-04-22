# llm-gateway-sse

Spring Boot 3 WebFlux + SSE LLM 网关（MVP 正在搭建中）。

当前已完成：
- `docker compose up -d --build` 一键启动：应用 + PostgreSQL(pgvector) + Redis + RabbitMQ
- Actuator 健康检查：`GET /actuator/health`
- SSE Demo（Mock 上游）：`POST /v1/chat/stream`（`meta/delta/done/error`）

后续里程碑（M1+）会补齐：
- 真实上游适配（非 mock）
- 更完整的可观测与治理（限流/重试/配额等）

## 快速开始（Docker Compose）

### 1) 准备环境变量

复制一份环境变量文件（可按需修改端口/账号密码）：

```bash
copy .env.example .env
```

### 2) 一键启动

```bash
docker compose up -d --build
docker compose ps
```

你应该能看到 `app/postgres/redis/rabbitmq` 均为 `healthy`。

### 3) 验证应用健康

```bash
curl -fsS http://localhost:8080/actuator/health
```

预期输出类似：

```json
{"status":"UP","groups":["liveness","readiness"]}
```

### 4) 停止

```bash
docker compose down
```

## 常用地址与端口

- 应用：`http://localhost:8080`
- 健康检查：`GET http://localhost:8080/actuator/health`
- Prometheus 指标：`GET http://localhost:8080/actuator/prometheus`
- SSE 接口：`POST http://localhost:8080/v1/chat/stream`
- Prometheus（obs profile）：`http://localhost:9090`
- Grafana（obs profile）：`http://localhost:3000`
- Jaeger（obs profile）：`http://localhost:16686`
- PostgreSQL：`localhost:${POSTGRES_PORT:-5432}`
- Redis：`localhost:${REDIS_PORT:-6379}`
- RabbitMQ：
  - AMQP：`localhost:${RABBITMQ_PORT:-5785}`
  - 管理台：`http://localhost:${RABBITMQ_MANAGEMENT_PORT:-15672}`（默认账号密码见 `.env`）

## 配置说明（M0 占位）

当前 `src/main/resources/application.yml` 里已预留以下配置读取方式：

- API Key（可选，默认关闭）：
  - `llm-gateway.security.api-key.enabled=true`
  - `llm-gateway.security.api-key.pepper`（建议通过环境变量 `API_KEY_PEPPER` 提供）
  - `llm-gateway.security.api-key.hashes`（SHA-256 hex 列表；网关会对 `pepper:apiKey` 计算 SHA-256 后比对）
- Redis 限流（可选，默认关闭）：
  - `llm-gateway.ratelimit.enabled=true`
  - `llm-gateway.ratelimit.rpm=100`（每分钟请求数）
  - `llm-gateway.ratelimit.window-seconds=65`
- Redis：`REDIS_HOST` / `REDIS_PORT`
- RabbitMQ：`RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS`
- Postgres：`POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`

注意：M0 阶段应用尚未连接数据库/消息队列做业务逻辑，这些配置会在后续里程碑逐步启用。

## 开发方式（本地跑应用，依赖用 Compose）

先启动依赖（不需要启动 `app` 容器）：

```bash
docker compose up -d postgres redis rabbitmq
```

然后在本机启动 Spring Boot：

```bash
mvn -DskipTests spring-boot:run
```

## 可观测（M3）

启动带观测栈（Prometheus/Grafana/Jaeger）的本地环境：

```bash
docker compose --profile obs up -d --build
docker compose ps
```

验收要点：
- `curl -fsS http://localhost:8080/actuator/prometheus` 有输出
- 在 Prometheus（`http://localhost:9090`）查询 `gateway_chat_stream_requests_total`
- 发起一次 `/v1/chat/stream` 后，在 Jaeger（`http://localhost:16686`）能看到 trace（服务名 `llm-gateway-sse`）

## SSE Demo（Mock 上游）

### 1) 正常流式（meta -> delta... -> done）

```bash
curl -N ^
  -H "Accept: text/event-stream" ^
  -H "Content-Type: application/json" ^
  -H "X-Request-Id: req_demo_001" ^
  -d "{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"timeoutMs\":2000}" ^
  http://localhost:8080/v1/chat/stream
```

### 2) 上游错误降级（meta -> delta... -> error）

```bash
curl -N ^
  -H "Accept: text/event-stream" ^
  -H "Content-Type: application/json" ^
  -H "X-Request-Id: req_demo_002" ^
  -d "{\"model\":\"mock_error\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"timeoutMs\":2000}" ^
  http://localhost:8080/v1/chat/stream
```

### 3) 上游超时降级（meta -> error）

```bash
curl -N ^
  -H "Accept: text/event-stream" ^
  -H "Content-Type: application/json" ^
  -H "X-Request-Id: req_demo_003" ^
  -d "{\"model\":\"mock_slow\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"timeoutMs\":1}" ^
  http://localhost:8080/v1/chat/stream
```

### 4) 客户端断连（取消上游）

运行“正常流式”命令后按 `Ctrl+C` 中断连接，然后查看日志应包含：
- `clientAborted=true`
- `mockUpstream=cancelled`

```bash
docker compose logs -f app
  http://localhost:8080/v1/chat/stream
```

## M4（异步与成本估算）已落地
- `done` 事件包含：`tokenEstimated`、`costEstimated`
- 请求完成后会记录：`requestId/model/status/latency/token/cost/costAlgorithmVersion`
- 已发布 RabbitMQ 事件：`request.completed`（默认 exchange: `llm.gateway.events`）

### M4 相关配置
- `llm-gateway.m4.persistence.enabled`：是否启用完成记录持久化（默认 `true`）
- `llm-gateway.m4.persistence.connect-timeout-seconds`：PostgreSQL 连接超时秒数（默认 `2`）
- `llm-gateway.m4.events.enabled`：是否启用完成事件发布（默认 `true`）
- `llm-gateway.m4.events.exchange`：事件交换机（默认 `llm.gateway.events`）
- `llm-gateway.m4.events.routing-key`：路由键（默认 `request.completed`）
- `llm-gateway.m4.cost-estimation.chars-per-token`：字符到 token 的估算比例（默认 `4`）
- `llm-gateway.m4.cost-estimation.usd-per-1k-tokens`：每 1k token 估算美元成本（默认 `0.001`）
- `llm-gateway.m4.cost-estimation.algorithm-version`：估算算法版本（默认 `v1_chars_div_4`）

### 本地最小验证（PowerShell）
```powershell
# 1) 启动依赖
docker compose up -d postgres redis rabbitmq

# 2) 发起一次流式请求
Invoke-WebRequest -Uri "http://localhost:8080/v1/chat/stream" `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{Accept="text/event-stream"; "X-Request-Id"="req_m4_demo_001"} `
  -Body '{"model":"mock","messages":[{"role":"user","content":"hi"}],"timeoutMs":2000}'

# 3) 查看 RabbitMQ 管理台（默认）
# http://localhost:15672
```

## Real Upstream (OpenAI) Minimal Setup
- Set `UPSTREAM_MODE=openai`
- Set `OPENAI_API_KEY` (required)
- Optional: `OPENAI_BASE_URL` (default `https://api.openai.com`)
- Optional: `OPENAI_CHAT_COMPLETIONS_PATH` (default `/v1/chat/completions`)
- Optional: `OPENAI_DEFAULT_MODEL` (default `gpt-4o-mini`)

```powershell
$env:UPSTREAM_MODE="openai"
$env:OPENAI_API_KEY="<YOUR_OPENAI_API_KEY>"
$env:OPENAI_DEFAULT_MODEL="gpt-4o-mini"
mvn spring-boot:run
```

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/v1/chat/stream" `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{Accept="text/event-stream"; "X-Request-Id"="req_real_upstream_001"} `
  -Body '{"messages":[{"role":"user","content":"Use one sentence to explain Spring WebFlux."}],"timeoutMs":30000}'
```
