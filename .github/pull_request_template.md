## 变更说明
- 

## 关联 Issue
- Closes #

## 验收方式（必填）
给出你本地验证的命令与结果（至少一条）：

```bash
docker compose up -d --build
curl -fsS http://localhost:8080/actuator/health
```

## Checklist
- [ ] 变更范围已最小化，避免无关改动
- [ ] 本地自测通过（贴出命令）
- [ ] 文档已更新（README/配置说明/示例）
- [ ] （如涉及对外契约）已更新接口/事件协议与示例
- [ ] （如涉及可观测）日志字段/指标命名一致，可用 requestId 串联

## 备注（可选）
- 

