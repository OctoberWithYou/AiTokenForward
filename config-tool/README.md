# AI 模型转发系统 - 配置工具

图形化 Web 界面配置工具,无需手动编辑 YAML 配置文件。

## 功能

- 🖥️ 服务器配置 - 一键配置公网服务器
- 📱 客户端配置 - 可视化配置内网客户端
- 🤖 模型管理 - 界面化添加/删除 AI 模型
- 🚀 一键启动 - 从界面直接启动服务 (需手动运行jar)
- 💾 配置保存 - 自动生成 server.yaml 和 agent.yaml

## 使用方法

### 启动配置工具

```bash
# 方式一: Gradle 运行
cd config-tool
gradle build
java -jar build/libs/config-tool.jar

# 方式二: 直接运行 JAR
java -jar config-tool/build/libs/config-tool.jar
```

然后在浏览器打开: **http://localhost:8888**

## 界面功能说明

### 服务器配置 (公网部署)

| 配置项 | 说明 |
|--------|------|
| 监听地址 | 服务器绑定的 IP,0.0.0.0 表示所有网卡 |
| 监听端口 | HTTP 服务端口,默认 8080 |
| HTTPS设置 | 生产环境建议启用 |
| Token文件 | 存放客户端认证 Token 的文件 |
| Agent连接Token | 内网客户端连接时使用的认证 |

### 客户端配置 (内网部署)

| 配置项 | 说明 |
|--------|------|
| WebSocket服务器地址 | 公网服务器地址,如 ws://your-server.com:8081/agent |
| 连接Token | 与服务器配置的 Agent Token 一致 |
| 模型配置 | 要转发的 AI 模型列表 |

### 支持的模型提供商

- **OpenAI**: GPT-4, GPT-3.5, Embeddings 等
- **Anthropic Claude**: Claude 3 等
- **Azure OpenAI**: Azure 部署的模型

## 配置字段说明

### 服务器配置 (server.yaml)

```yaml
server:
  host: "0.0.0.0"      # 监听地址
  port: 8080           # 监听端口
  ssl:
    enabled: false     # 是否启用HTTPS
    keyStore: "server.jks"
    keyStorePassword: "changeit"

externalClient:
  tokenFile: "config/external-token.txt"  # 客户端Token文件
  headerName: "X-Auth-Token"              # 认证Header

auth:
  apiKeys:
    - "sk-xxx"       # API Keys

agent:
  connection:
    token: "agent-token"  # Agent连接Token
    maxAgents: 10          # 最大Agent数
```

### 客户端配置 (agent.yaml)

```yaml
server:
  url: "ws://server:8081/agent"
  token: "agent-token"

models:
  - id: "gpt-4"
    provider: "openai"
    endpoint: "https://api.openai.com/v1"
    apiKey: "sk-xxx"

  - id: "claude-3-opus"
    provider: "anthropic"
    endpoint: "https://api.anthropic.com"
    apiKey: "sk-ant-xxx"
    apiVersion: "2023-06-01"
```

## 技术栈

- 前端: React + Vite
- 后端: Jetty 11 (嵌入式 Servlet 容器)
- 端口: 8888