# 设备维护工单 API 与数据规格

版本：v1
基础路径：`/api/v1`

## 1. 创建工单

`POST /work-orders`

```json
{
  "title": "CNC-07 spindle overheating",
  "description": "Temperature exceeded 95C and production was stopped.",
  "assetId": "CNC-07",
  "priority": "CRITICAL",
  "reportedBy": "operator-104",
  "idempotencyKey": "incident-20261106-0001"
}
```

成功返回 201 和完整工单。相同幂等键与相同请求返回同一结果；相同幂等键与不同请求返回 409。

## 2. 查询与搜索

- `GET /work-orders/{id}`：按 ID 查询。
- `GET /work-orders?assetId=&status=&priority=&createdFrom=&createdTo=&page=&size=`：分页搜索。
- page 从 0 开始，size 默认 20、最大 200，默认按创建时间倒序。

## 3. 状态动作

- `POST /work-orders/{id}/acknowledgement`
- `POST /work-orders/{id}/start`
- `POST /work-orders/{id}/resolution`
- `POST /work-orders/{id}/closure`
- `POST /work-orders/{id}/cancellation`

所有状态动作包含动作时间与 `expectedVersion`。受理包含协调员，开始包含工程师，解决包含摘要，取消包含原因。关闭只允许主管角色。

## 4. SLA 表示

查询响应包含 `acknowledgementDeadline`、`resolutionDeadline`、两个 overdue 标志和 `evaluatedAt`，避免客户端误认为 SLA 标志永久不变。

## 5. 错误结构

```json
{
  "code": "WORK_ORDER_INVALID_STATE",
  "message": "Only IN_PROGRESS work orders can be resolved",
  "details": {
    "currentStatus": "ACKNOWLEDGED",
    "requiredStatus": "IN_PROGRESS"
  },
  "traceId": "2f56f5d9f8b44d2a"
}
```

稳定错误码包括 `WORK_ORDER_NOT_FOUND`、`WORK_ORDER_INVALID_STATE`、`WORK_ORDER_VERSION_CONFLICT`、`WORK_ORDER_INVALID_TIME`、`WORK_ORDER_VALIDATION_FAILED`、`IDEMPOTENCY_CONFLICT` 和 `FORBIDDEN_OPERATION`。

## 6. 数据约束

- title：1..200 字符。
- description：1..10000 字符。
- assetId：1..100 字符。
- actor ID：1..100 字符。
- 解决摘要和取消原因：1..4000 字符。
- 时间使用 ISO-8601 UTC instant。
- 客户端不得设置状态、截止时间、审计时间或初始版本。

## 7. 并发与幂等

- 状态变更包含 `expectedVersion`，不一致返回 409。
- 创建支持 `Idempotency-Key`。
- 重复状态动作返回 409 或原结果，正式实现必须固定一种语义并记录在 API 决策中。

## 8. 安全

- API 使用 Bearer 认证。
- 服务端从令牌解析主体和角色，不信任正文角色字段。
- actor 字段必须与认证主体或授权代理关系匹配。
- 错误响应不得返回堆栈、SQL、凭据或内部网络地址。
