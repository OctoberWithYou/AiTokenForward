# AI Model Proxy Forward

一个用于转发 AI 模型 API 调用的代理系统，解决内网模型 key 无法被外网访问的问题。

## 安全警告

> **⚠️ 重要：生产环境必须使用 HTTPS**
>
> - Token 认证信息通过 HTTP Header 传递，**必须使用 HTTPS 加密传输**
> - 禁止在生产环境使用 HTTP，否则 Token 会被明文截获
> - 请参考「安全部署」章节配置 SSL/TLS

## 功能特性

- **多模型支持**：支持 OpenAI、Anthropic Claude、Azure OpenAI 等多种模型
- **WebSocket 长连接**：Agent 与 Server 之间保持持久连接
- **OpenAI 兼容 API**：外网客户端使用标准 OpenAI API 格式调用
- **安全认证**：外部客户端 Token 认证 + Agent Token 认证
- **自动重连**：Agent 断线自动重连
- **配置灵活**：支持 YAML 配置文件

## 系统架构

```
外网客户端 (应用/用户)
        │
        │ HTTPS (OpenAI 兼容 API)
        ▼
┌───────────────────────┐
│   Forward Server      │  ◄── 公网可访问
│   (端口: 8080)        │
└───────────────────────┘
        │
        │ WebSocket (wss://)
        ▼
┌───────────────────────┐
│   Agent               │  ◄── 运行在内网 Windows
│   (内网可访问外网)    │
└───────────────────────┘
        │
        │ 直接调用
        ▼
┌───────────────────────┐
│   AI 模型服务          │
│ (OpenAI/Claude/Azure) │
└───────────────────────┘
```

## 快速开始

### 环境要求

- Java 17+
- Gradle 8.x

### 构建项目

```bash
# 构建所有模块
gradle build

# 或分别构建
gradle :server:build
gradle :agent:build
```

构建产物位于：
- `server/build/libs/forward-server-1.0-SNAPSHOT.jar`
- `agent/build/libs/forward-agent-1.0-SNAPSHOT.jar`

### 配置说明

#### Server 配置 (server-config.yaml)

```yaml
server:
  host: "0.0.0.0"
  port: 8080
  ssl:
    enabled: false

externalClient:
  tokenFile: "config/external-token.txt"  # 外部客户端认证 token 文件
  headerName: "X-Auth-Token"              # Token Header 名称

auth:
  apiKeys:
    - "sk-forward-key-001"

agent:
  connection:
    token: "agent-secret-token-12345"     # Agent 连接认证 token
    maxAgents: 10
```

#### Agent 配置 (agent-config.yaml)

```yaml
server:
  url: "wss://your-server.com/agent"      # 服务器 WebSocket 地址
  token: "agent-secret-token-12345"       # 与服务器配置的 token 一致

models:
  - id: "gpt-4"
    provider: "openai"
    endpoint: "https://api.openai.com/v1"
    apiKey: "sk-your-openai-key"

  - id: "claude-3-opus"
    provider: "anthropic"
    endpoint: "https://api.anthropic.com"
    apiKey: "sk-ant-your-key"
    apiVersion: "2023-06-01"

reconnect:
  enabled: true
  maxAttempts: 10
  initialDelayMs: 1000
  maxDelayMs: 60000
```

### 部署

#### 1. 部署 Server (公网服务器)

```bash
java -jar server/build/libs/forward-server-1.0-SNAPSHOT.jar \
  --config server/src/main/resources/config/server-config.yaml
```

#### 2. 部署 Agent (内网 Windows)

```bash
java -jar agent/build/libs/forward-agent-1.0-SNAPSHOT.jar \
  --config agent/src/main/resources/config/agent-config.yaml
```

#### 3. 配置外部客户端 Token

在服务器上创建 token 文件：

```bash
echo "your-client-secret-token" > config/external-token.txt
```

#### 4. 安全部署（必读）

**生产环境必须启用 HTTPS！**

```yaml
# server-config.yaml
server:
  host: "0.0.0.0"
  port: 443
  ssl:
    enabled: true
    keyStore: "server.jks"       # Java Keystore 文件
    keyStorePassword: "changeit" # 密钥库密码
```

生成自签名证书（测试用）或使用 Let's Encrypt 获取正式证书：

```bash
# 生成自签名证书（仅用于测试）
keytool -genkey -alias server -keyalg RSA -keystore server.jks -keysize 2048 -validity 3650
```

### 使用示例

```bash
# 测试 Chat Completions
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: your-client-secret-token" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'

# 测试 Models List
curl -H "X-Auth-Token: your-client-secret-token" \
  http://localhost:8080/v1/models
```

## 项目结构

```
Forward/
├── server/                    # 转发服务器模块
│   ├── src/main/java/
│   │   └── org/ljc/
│   │       ├── server/        # 服务器核心类
│   │       ├── config/        # 配置类
│   │       └── common/       # 通用类
│   └── src/main/resources/
│       └── config/           # 配置文件
├── agent/                     # 内网 Agent 模块
│   ├── src/main/java/
│   │   └── org/ljc/
│   │       ├── agent/         # Agent 核心类
│   │       ├── config/        # 配置类
│   │       └── common/        # 通用类
│   └── src/main/resources/
│       └── config/           # 配置文件
├── build.gradle               # 根构建配置
└── settings.gradle            # 项目设置
```

## 测试

```bash
# 运行所有测试
gradle test

# 运行特定模块测试
gradle :server:test
gradle :agent:test
```

## 安全注意事项

> **⚠️ 警告：生产环境必须使用 HTTPS**
>
> Token 通过 HTTP Header 传递，明文 HTTP 会导致 Token 被截获！

1. **API Key 保护**：Agent 配置文件中的 API Key 需要妥善保管，建议使用环境变量
2. **Token 安全**：外部客户端 token 和 Agent token 应使用强随机字符串
3. **SSL/TLS**：**生产环境必须启用 HTTPS**（见上文「安全部署」章节）
4. **网络隔离**：确保内网 Agent 的安全
5. **Token 文件权限**：确保 token 文件权限设置为仅服务用户可读

## 许可证

MIT License