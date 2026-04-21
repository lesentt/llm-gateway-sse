# Contributing

欢迎贡献 `llm-gateway-sse`。本项目目标与验收标准见 `README.md` 与里程碑（M0/M1/M2…）。

## 开发约定

- **主分支可运行**：`main` 分支应始终保持可构建、可启动。
- **小步提交**：每个 PR 只做一件事，并附带可复现的验证命令。
- **可观测优先**：涉及请求链路的变更，优先保证 `requestId` 可串联日志/指标/trace。

## 本地开发

### 方式 A：Compose 跑全套（推荐，最接近验收）

```bash
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8080/actuator/health
```

### 方式 B：依赖用 Compose，本地跑 Spring Boot

```bash
docker compose up -d postgres redis rabbitmq
mvn -DskipTests spring-boot:run
curl -fsS http://localhost:8080/actuator/health
```

## 常用命令（最小 CLI）

仓库提供一个 PowerShell 脚本封装常用命令：

```powershell
.\tools\gw.ps1 help
.\tools\gw.ps1 up
.\tools\gw.ps1 ps
.\tools\gw.ps1 logs app
.\tools\gw.ps1 test
```

## 分支与提交信息

- 分支命名：`feature/<milestone>-<topic>`、`fix/<topic>`、`chore/<topic>`
- 提交信息建议：`M1: ...` / `fix: ...` / `chore: ...`（保持简短明确）

## PR 要求

- 描述清楚“做了什么/为什么/怎么验收”
- 至少提供一条可复制粘贴的验证命令
- 如变更对外 API / SSE 事件协议：同步更新 `README.md` 与示例

