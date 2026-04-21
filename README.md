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
  - AMQP：`localhost:${RABBITMQ_PORT:-5672}`
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
