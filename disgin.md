## 模型调用代理转发项目

诉求：我在内网有一些模型的key，只有内网能访问，但是我的内网windows可以访问外网，我有台服务器，我计划用服务器做转发，使得外网可以通过访问服务器进而访问和使用内网的模型

---

# 详细设计方案

## 1. 项目概述

### 1.1 项目背景

用户需要构建一个 **AI 模型代理转发系统**，解决以下场景：
- 内网服务器上保存了多个 AI 模型的 API Key（如 OpenAI、Anthropic Claude、Azure OpenAI 等）
- 内网 Windows 机器可以访问外网，但外网无法直接访问内网
- 有一台具有公网访问能力的中间服务器（转发服务器）
- 目标：外网客户端通过访问转发服务器，间接调用内网的 AI 模型

### 1.2 系统架构

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│   外网客户端     │ ───► │   转发服务器     │ ───► │  内网 Agent     │ ───► 内网 AI 模型
│ (各种应用/用户)  │      │  (公网可访问)    │      │ (运行在内网Windows)│      (OpenAI/Claude等)
└─────────────────┘      └─────────────────┘      └─────────────────┘
        │                       │                        │
        │   标准 API (HTTPS)    │   WebSocket/HTTP       │  直接调用
        │   (OpenAI兼容)        │   (内网隧道)           │
```

### 1.3 核心组件

| 组件 | 位置 | 功能 |
|------|------|------|
| **Forward Server** | 公网服务器 | 接收外网请求，转发给内网 Agent，返回响应给客户端 |
| **Agent** | 内网 Windows | 维护与服务器的连接，接收请求，调用内网 AI 模型 |
| **Config** | 配置文件 | 存储模型配置、认证信息等 |

---

## 2. 功能需求

### 2.1 转发服务器 (Forward Server)

#### 2.1.1 基本功能
- **HTTP/HTTPS 服务**：提供 RESTful API 端点，兼容 OpenAI API 格式
- **多模型支持**：支持多种 AI 模型的请求路由
- **连接管理**：维护与内网 Agent 的长连接
- **请求转发**：将接收到的请求透明转发给 Agent
- **响应回传**：将 AI 模型的响应返回给客户端

#### 2.1.2 API 端点设计

```
# Chat Completion (OpenAI 兼容)
POST /v1/chat/completions

# Embeddings
POST /v1/embeddings

# Models List
GET /v1/models

# 认证
GET /v1/models - 需要 API Key 验证
```

#### 2.1.3 配置项
- 服务器监听端口 (默认: 8080)
- SSL/TLS 证书配置
- 可配置的 API Key 验证
- Agent 连接池配置

### 2.2 内网 Agent

#### 2.2.1 基本功能
- **主动连接**：启动时主动连接转发服务器
- **长连接维护**：使用 WebSocket 保持与服务器的持久连接
- **请求接收**：接收服务器转发的请求
- **模型调用**：使用内网 API Key 调用 AI 模型
- **响应返回**：将模型响应通过连接返回给服务器
- **重连机制**：连接断开后自动重连

#### 2.2.2 支持的模型提供商

| 提供商 | 支持状态 | 备注 |
|--------|----------|------|
| OpenAI | ✅ | 支持 GPT-4, GPT-3.5 |
| Anthropic | ✅ | 支持 Claude 3 |
| Azure OpenAI | ✅ | 支持 Azure 部署的模型 |
| 自定义 URL | ✅ | 支持任意兼容 OpenAI API 的服务 |

#### 2.2.3 配置项
- 服务器地址 (WebSocket URL)
- 认证 token
- 模型配置列表 (每个模型的 endpoint、API Key 等)
- 重连策略配置
- 日志级别

---

## 3. 技术方案

### 3.1 技术选型

| 组件 | 技术栈 | 版本 |
|------|--------|------|
| 编程语言 | Java | 17+ |
| 构建工具 | Gradle | 8.x |
| HTTP 服务器 | Java HTTP Server / Jetty | 内置 / 12.x |
| HTTP 客户端 | OkHttp | 4.x |
| WebSocket | Java WebSocket API / OkHttp | 内置 / 4.x |
| JSON 处理 | Jackson | 2.x |
| 配置管理 | 自定义 / SnakeYAML | - |
| 日志 | SLF4J + Logback | 2.x |

### 3.2 通信协议

#### 3.2.1 Agent ↔ Server 通信

使用 **WebSocket** 协议进行双向通信：

```
Agent (Client)                          Server
    │                                     │
    │──── WebSocket Connect (wss://) ────►│
    │◄───── Connection Ack ───────────────│
    │                                     │
    │──── Register (包含认证信息) ────────►│
    │◄───── Register Ack ─────────────────│
    │                                     │
    │         (长连接保持)                 │
    │                                     │
    │◄───── Request (来自外网客户端) ──────│
    │──── Response (AI 模型返回) ─────────►│
    │                                     │
```

#### 3.2.2 请求/响应格式

**Agent 注册消息：**
```json
{
  "type": "register",
  "token": "agent-auth-token",
  "models": [
    {
      "id": "gpt-4",
      "provider": "openai",
      "endpoint": "https://api.openai.com",
      "apiKey": "sk-xxx"
    }
  ]
}
```

**转发请求消息：**
```json
{
  "type": "request",
  "requestId": "uuid",
  "model": "gpt-4",
  "endpoint": "/v1/chat/completions",
  "payload": {
    "model": "gpt-4",
    "messages": [...]
  }
}
```

**响应消息：**
```json
{
  "type": "response",
  "requestId": "uuid",
  "status": 200,
  "body": {...}
}
```

### 3.3 项目结构

```
src/
├── main/
│   ├── java/org/ljc/
│   │   ├── Main.java                 # 入口类
│   │   ├── Server.java               # 转发服务器主类
│   │   ├── Agent.java                # Agent 主类
│   │   ├── config/
│   │   │   ├── Config.java           # 配置类
│   │   │   ├── ServerConfig.java     # 服务器配置
│   │   │   └── AgentConfig.java      # Agent 配置
│   │   ├── server/
│   │   │   ├── HttpHandler.java      # HTTP 请求处理
│   │   │   ├── WebSocketHandler.java # WebSocket 处理
│   │   │   ├── Router.java           # 请求路由
│   │   │   └── AuthManager.java      # 认证管理
│   │   ├── agent/
│   │   │   ├── AgentClient.java      # Agent WebSocket 客户端
│   │   │   ├── ModelCaller.java      # 模型调用器
│   │   │   └── ReconnectManager.java # 重连管理
│   │   ├── common/
│   │   │   ├── Message.java          # 消息定义
│   │   │   ├── RequestDispatcher.java# 请求分发
│   │   │   └── Logger.java           # 日志工具
│   │   └── util/
│   │       ├── JsonUtil.java         # JSON 工具
│   │       └── HttpUtil.java         # HTTP 工具
│   └── resources/
│       ├── config.yaml               # 配置文件
│       ├── server.jks                # SSL 证书
│       └── logback.xml               # 日志配置
└── test/
    └── java/...
```

---

## 4. 详细设计

### 4.1 配置设计

#### 4.1.1 配置文件格式 (YAML)

**服务器配置 (server-config.yaml)：**
```yaml
server:
  host: "0.0.0.0"
  port: 8080
  ssl:
    enabled: false
    keyStore: "server.jks"
    keyStorePassword: "changeit"

auth:
  apiKeys:
    - "sk-forward-key-001"
    - "sk-forward-key-002"

agent:
  connection:
    token: "agent-secret-token"
    maxAgents: 10
```

**Agent 配置 (agent-config.yaml)：**
```yaml
server:
  url: "wss://your-server.com/agent"
  token: "agent-secret-token"

models:
  - id: "gpt-4"
    provider: "openai"
    endpoint: "https://api.openai.com/v1"
    apiKey: "sk-openai-key"

  - id: "claude-3-opus"
    provider: "anthropic"
    endpoint: "https://api.anthropic.com"
    apiKey: "sk-ant-key"
    apiVersion: "2023-06-01"

  - id: "azure-gpt-4"
    provider: "azure"
    endpoint: "https://your-resource.openai.azure.com"
    apiKey: "azure-key"
    apiVersion: "2024-02-01"

reconnect:
  enabled: true
  maxAttempts: 10
  initialDelayMs: 1000
  maxDelayMs: 60000
```

### 4.2 核心流程

#### 4.2.1 服务器请求处理流程

```
1. 外网客户端发送 POST /v1/chat/completions
2. HttpHandler 接收请求
3. AuthManager 验证 API Key
4. 从请求体中提取 model 字段
5. 查找对应模型的 Agent 连接
6. 通过 WebSocket 发送请求给 Agent
7. 等待 Agent 返回响应
8. 将响应返回给客户端
```

#### 4.2.2 Agent 请求处理流程

```
1. Agent 启动，连接服务器 WebSocket
2. 发送注册消息 (包含模型配置)
3. 服务器确认注册
4. 等待请求消息
5. 收到请求后，解析 model 和 payload
6. 使用对应模型的配置调用 AI API
7. 将响应发送回服务器
8. 继续等待下一个请求
```

### 4.3 错误处理

| 场景 | 处理方式 |
|------|----------|
| Agent 未连接 | 返回 503 Service Unavailable |
| 模型调用失败 | 返回原始错误信息，状态码保持 |
| 连接断开 | Agent 自动重连，服务器端请求超时 |
| 认证失败 | 返回 401 Unauthorized |
| 请求超时 | 返回 504 Gateway Timeout |

### 4.4 安全性考虑

1. **API Key 保护**：
   - Agent 端配置文件的 API Key 需要加密存储
   - 传输过程使用 SSL/TLS

2. **认证机制**：
   - 服务器对客户端使用 API Key 认证
   - 服务器对 Agent 使用 Token 认证

3. **请求限制**：
   - 可配置速率限制
   - 可配置请求超时时间

---

## 5. 部署方案

### 5.1 服务器部署

```bash
# 打包
./gradlew build

# 运行服务器
java -jar build/libs/forward-1.0-SNAPSHOT.jar server --config server-config.yaml
```

### 5.2 Agent 部署 (内网 Windows)

```bash
# 打包
./gradlew build

# 运行 Agent
java -jar build/libs/forward-1.0-SNAPSHOT.jar agent --config agent-config.yaml
```

---

## 6. 验证方案

### 6.1 功能测试

1. **启动服务器**：验证 HTTP 服务正常监听
2. **启动 Agent**：验证 WebSocket 连接成功
3. **发送请求**：使用 curl 或 Postman 发送测试请求
4. **验证响应**：确认 AI 模型响应正确返回

### 6.2 测试命令示例

```bash
# 测试 Chat Completions
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-forward-key-001" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'

# 测试 Models List
curl -H "Authorization: Bearer sk-forward-key-001" \
  http://localhost:8080/v1/models
```

---

## 7. 后续优化 (可选)

- [ ] 流式响应支持 (Server-Sent Events)
- [ ] 多 Agent 负载均衡
- [ ] 请求日志和监控
- [ ] API Key 加密存储
- [ ] Web 管理界面
- [ ] Docker 支持