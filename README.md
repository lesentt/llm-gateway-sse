# llm-gateway-sse

Spring Boot 3 WebFlux + SSE LLM 网关（MVP 正在搭建中）。

当前已完成：
- `docker compose up -d --build` 一键启动：应用 + PostgreSQL(pgvector) + Redis + RabbitMQ
- Actuator 健康检查：`GET /actuator/health`

后续里程碑（M1+）会补齐：
- `POST /v1/chat/stream`（SSE）：`meta/delta/done/error`
- 统一错误结构、超时/降级、断连取消、可观测与文档等

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
- PostgreSQL：`localhost:${POSTGRES_PORT:-5432}`
- Redis：`localhost:${REDIS_PORT:-6379}`
- RabbitMQ：
  - AMQP：`localhost:${RABBITMQ_PORT:-5672}`
  - 管理台：`http://localhost:${RABBITMQ_MANAGEMENT_PORT:-15672}`（默认账号密码见 `.env`）

## 配置说明（M0 占位）

当前 `src/main/resources/application.yml` 里已预留以下配置读取方式：

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

