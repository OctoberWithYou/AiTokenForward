# AI 模型转发系统 - 配置工具

这是一个用于生成服务器和客户端配置文件的图形化工具。

## 功能
- 简体中文界面
- 服务器配置生成
- 客户端配置生成
- 一键启动服务器/客户端

## 使用方法

### 方式一：命令行
```bash
# 编译
cd config-tool
gradle build

# 运行
gradle run
```

### 方式二：直接运行 JAR
```bash
java -jar build/libs/config-tool.jar
```

## 配置说明

### 服务器配置 (server.yaml)
```yaml
server:
  host: 服务器监听地址
  port: 服务器端口
  ssl:
    enabled: 是否启用HTTPS
    keyStore: 密钥库路径
    keyStorePassword: 密钥库密码

externalClient:
  tokenFile: 客户端Token文件路径
  headerName: 认证Header名称

auth:
  apiKeys:
    - API密钥列表

agent:
  connection:
    token: Agent连接Token
    maxAgents: 最大Agent数量
```

### 客户端配置 (agent.yaml)
```yaml
server:
  url: WebSocket服务器地址
  token: 连接Token

models:
  - id: 模型ID
    provider: 模型提供商
    endpoint: API端点
    apiKey: API密钥
    apiVersion: API版本
```