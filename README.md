# Maintenance SLA Service

这是一个用于项目级 Agentic 全链路验证的工业样例仓库。项目模拟制造企业的设备维护工单核心域，重点验证工单状态机、优先级 SLA、逾期告警和可审计决策。

## 业务目标

- 统一接收生产设备故障与预防性维护工单。
- 按优先级计算响应和解决时限。
- 防止非法状态跳转和关闭未完成工单。
- 为后续 REST API、持久化、通知和审计实现提供稳定领域核心。

## 当前范围

- Java 17 领域模型。
- 工单创建、受理、开始处理、解决、关闭与取消。
- SLA 截止时间计算与逾期判定。
- JUnit 5 自动化测试。
- GitHub Actions 持续集成。

## 工程制品

- [软件需求规格说明书](docs/SRS.md)
- [架构设计说明书](docs/ARCHITECTURE.md)
- [API 与数据规格](docs/API-SPEC.md)
- [验证与验收计划](docs/TEST-PLAN.md)

## 构建

```bash
mvn --batch-mode verify
```

## 分支策略

本仓库的 Agentic 测试只使用 `codex/agentic-real-*` 专用分支，不直接写入默认分支。所有后续实现应通过代码审阅和 CI 后再决定是否合并。
