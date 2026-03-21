# AI 模型代理转发系统 - 产品文档

## 1. 产品概述

### 1.1 产品背景

在企业环境中，AI 模型的 API Key 通常存储在内网服务器上，由于安全策略，外网无法直接访问内网资源。本产品通过部署一个转发服务器，让外网客户端能够通过访问转发服务器，间接使用内网的 AI 模型。

### 1.2 产品定位

- **目标用户**：拥有内网 AI 模型 API Key 的企业或个人开发者
- **使用场景**：需要将内网 AI 能力暴露给外网使用的场景
- **核心价值**：安全、高效、简单地实现内外网 AI 资源互通

### 1.3 产品特性

| 特性 | 说明 |
|------|------|
| 多模型支持 | 支持 OpenAI、Anthropic Claude、Azure OpenAI |
| 标准兼容 | 完全兼容 OpenAI API 格式，客户端无需修改代码 |
| 安全认证 | Token 认证机制，确保只有授权客户端可以使用 |
| 自动重连 | Agent 断线自动重连，保证服务持续可用 |
| 灵活配置 | YAML 配置文件，支持多种参数调整 |

## 2. 系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         外网环境                                │
│                                                                 │
│   ┌──────────────┐                                             │
│   │ 外网客户端    │ ──► 标准 OpenAI API ──►                     │
│   │ (应用/用户)   │     POST /v1/chat/completions              │
│   └──────────────┘                                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ HTTPS (JSON)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       公网服务器                                 │
│                                                                  │
│   ┌──────────────────────────────────────────────────────────┐ │
│   │                  Forward Server                          │ │
│   │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │ │
│   │  │ HTTP Server │  │ WebSocket   │  │   Auth Manager  │  │ │
│   │  │ (8080)      │  │ Handler     │  │   (Token验证)   │  │ │
│   │  └─────────────┘  └─────────────┘  └─────────────────┘  │ │
│   └──────────────────────────────────────────────────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ WebSocket (wss://)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       内网环境                                   │
│                                                                  │
│   ┌──────────────────────────────────────────────────────────┐ │
│   │                      Agent                                │ │
│   │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │ │
│   │  │ WebSocket   │  │   Model     │  │ Reconnect       │  │ │
│   │  │ Client      │  │   Caller    │  │ Manager         │  │ │
│   │  └─────────────┘  └─────────────┘  └─────────────────┘  │ │
│   └──────────────────────────────────────────────────────────┘ │
│                              │                                   │
│                              ▼                                   │
│   ┌──────────────────────────────────────────────────────────┐ │
│   │                  AI 模型服务                              │ │
│   │         (OpenAI / Claude / Azure OpenAI)                  │ │
│   └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 组件说明

#### Forward Server (转发服务器)

部署在公网可访问的服务器上，负责：
- 接收外网客户端的 HTTP 请求
- 验证外部客户端 Token
- 维护与内网 Agent 的 WebSocket 连接
- 将请求转发给 Agent 并返回响应

#### Agent (内网代理)

部署在内网 Windows 机器上，负责：
- 主动连接转发服务器 (WebSocket)
- 接收服务器转发的请求
- 使用内网 API Key 调用 AI 模型
- 返回响应给服务器
- 断线自动重连

### 2.3 通信流程

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│  客户端  │                    │ Server  │                    │  Agent  │
└────┬────┘                    └────┬────┘                    └────┬────┘
     │                              │                              │
     │  1. POST /v1/chat/...       │                              │
     │ ─────────────────────────►  │                              │
     │                              │                              │
     │                             │  2. WebSocket 转发请求         │
     │                             │ ───────────────────────────► │
     │                              │                              │
     │                              │                              │ 3. 调用 AI API
     │                              │                              │ ──────────►
     │                              │                              │
     │                              │  4. 返回响应                 │
     │                              │ ◄─────────────────────────── │
     │                              │                              │
     │  5. 返回结果                 │                              │
     │ ◄────────────────────────── │                              │
     │                              │                              │
```

## 3. 功能规格

### 3.1 核心功能

| 功能 | 说明 | 状态 |
|------|------|------|
| Chat Completions | 对话补全 API | ✅ |
| Models List | 模型列表查询 | ✅ |
| Embeddings | 向量嵌入 API | ⏳ |
| 流式响应 | Server-Sent Events | ⏳ |

### 3.2 支持的模型提供商

| 提供商 | 支持状态 | 支持的模型 |
|--------|----------|------------|
| OpenAI | ✅ | GPT-4, GPT-3.5, etc. |
| Anthropic Claude | ✅ | Claude 3 Opus, Claude 3 Sonnet, etc. |
| Azure OpenAI | ✅ | Azure 部署的所有模型 |
| 自定义 URL | ✅ | 任何兼容 OpenAI API 的服务 |

### 3.3 认证机制

#### 外部客户端认证
- Token 存储在服务器指定文件中
- 客户端通过 HTTP Header 传递 Token
- 支持自定义 Header 名称

#### Agent 认证
- Agent 连接时必须提供正确的 Token
- 服务器验证 Token 后才允许注册

### 3.4 重连机制

Agent 端实现自动重连：
- 支持配置最大重试次数
- 指数退避策略 (初始延迟 1s，最大 60s)
- 可配置启用/禁用

## 4. 配置说明

### 4.1 Server 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| server.host | String | "0.0.0.0" | 监听地址 |
| server.port | int | 8080 | 监听端口 |
| server.ssl.enabled | boolean | false | 是否启用 HTTPS |
| server.ssl.keyStore | String | - | SSL 密钥库路径 |
| server.ssl.keyStorePassword | String | - | SSL 密钥库密码 |
| externalClient.tokenFile | String | - | 外部客户端 Token 文件 |
| externalClient.headerName | String | "X-Auth-Token" | Token Header 名称 |
| auth.apiKeys | List | [] | API Key 列表 (预留) |
| agent.connection.token | String | - | Agent 连接 Token |
| agent.connection.maxAgents | int | 10 | 最大 Agent 数量 |

### 4.2 Agent 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| server.url | String | - | 服务器 WebSocket URL |
| server.token | String | - | 连接服务器用的 Token |
| models | List | [] | 模型配置列表 |
| models[].id | String | - | 模型标识 |
| models[].provider | String | - | 模型提供商 |
| models[].endpoint | String | - | API 端点 |
| models[].apiKey | String | - | API Key |
| models[].apiVersion | String | - | API 版本 (可选) |
| reconnect.enabled | boolean | true | 是否启用重连 |
| reconnect.maxAttempts | int | 10 | 最大重试次数 |
| reconnect.initialDelayMs | long | 1000 | 初始重连延迟 (毫秒) |
| reconnect.maxDelayMs | long | 60000 | 最大重连延迟 (毫秒) |

## 5. 部署指南

### 5.1 环境要求

- **Java**: JDK 17 或更高版本
- **Gradle**: 8.x
- **内存**: 建议 512MB 以上
- **网络**: 服务器需要公网可访问，Agent 需要能访问外网

### 5.2 部署步骤

#### 步骤 1: 构建项目

```bash
git clone <repository-url>
cd Forward
gradle build
```

#### 步骤 2: 配置 Server

编辑 `server/src/main/resources/config/server-config.yaml`

#### 步骤 3: 部署 Server

```bash
java -jar server/build/libs/forward-server-1.0-SNAPSHOT.jar \
  --config server/src/main/resources/config/server-config.yaml
```

#### 步骤 4: 配置 Agent

编辑 `agent/src/main/resources/config/agent-config.yaml`

#### 步骤 5: 部署 Agent

```bash
java -jar agent/build/libs/forward-agent-1.0-SNAPSHOT.jar \
  --config agent/src/main/resources/config/agent-config.yaml
```

#### 步骤 6: 验证部署

```bash
# 检查健康状态
curl http://localhost:8080/health

# 列出模型
curl -H "X-Auth-Token: your-token" http://localhost:8080/v1/models
```

## 6. 使用指南

### 6.1 基础调用示例

#### Python 示例

```python
import openai

openai.api_base = "http://your-server:8080/v1"
openai.api_key = "dummy"  # Token 认证不需要真实 key

response = openai.ChatCompletion.create(
    model="gpt-4",
    messages=[{"role": "user", "content": "Hello!"}]
)

print(response)
```

#### cURL 示例

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: your-client-secret-token" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

### 6.2 错误处理

| 错误码 | 说明 | 解决方案 |
|--------|------|----------|
| 401 | 认证失败 | 检查 Token 是否正确 |
| 503 | 无可用 Agent | 检查 Agent 是否正常运行 |
| 504 | 请求超时 | 检查网络连接或增加超时时间 |

## 7. 监控与维护

### 7.1 日志位置

- Server: 输出到控制台，可重定向到文件
- Agent: 输出到控制台，可重定向到文件

### 7.2 健康检查

```bash
curl http://localhost:8080/health
# 返回: {"status": "ok", "agents": 1}
```

## 8. 后续规划

- [ ] 流式响应支持 (Server-Sent Events)
- [ ] 负载均衡支持 (多 Agent 场景)
- [ ] 请求日志和监控面板
- [ ] API Key 加密存储
- [ ] Docker 容器化部署
- [ ] 请求限流保护

## 9. 常见问题

### Q1: Agent 连接不上服务器？
- 检查服务器地址是否正确
- 检查 Token 是否匹配
- 检查网络连通性

### Q2: 请求返回 503？
- 确认 Agent 已成功连接服务器
- 检查 Agent 日志

### Q3: 如何添加新的模型？
- 在 Agent 配置中添加模型配置即可

## 10. 联系方式

- 问题反馈: [GitHub Issues](https://github.com/your-repo/issues)