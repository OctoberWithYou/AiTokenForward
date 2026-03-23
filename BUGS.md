# Bug 修复清单

## Bug #1: 服务器 WebSocket 无法接收消息

### 问题描述
- 服务器 WebSocket 实现不完整
- Agent 连接成功后，注册消息无法被服务器接收
- `handleOpen` 被调用，但 `handleMessage` 从未被调用
- 导致 Agent 未被正确注册到 AgentManager

### 根本原因
Server.java 中 `AgentWebSocketHttpHandler` 只处理了 WebSocket 握手(升级协议)，在发送 101 响应后没有启动线程读取 WebSocket 帧数据。

### 修复方案
1. 使用 Java NIO (Selector/ServerSocketChannel) 实现独立的 WebSocket 服务器
2. 在端口 HTTP 端口 + 1 上启动 WebSocket 服务器
3. 实现完整的 WebSocket 帧解析（支持 text、binary、ping、pong、close 帧）
4. 添加回调机制将 Agent 响应路由到 ClientHttpHandler 完成 HTTP 请求

### 状态: 已修复 ✓

---

## Bug #2: Agent 注册失败 (由 Bug #1 导致)

### 问题描述
- 集成测试返回 503 "No available agents"
- 日志显示 "Agent connected" 但没有 "Agent registered successfully"

### 根本原因
与 Bug #1 相关 - Agent 的注册消息从未到达 handleRegister 方法。修复 Bug #1 后，还需要修复响应回调未连接的问题：
- ClientHttpHandler.onAgentResponse() 方法存在但从未被调用
- AgentWebSocketHandler 处理响应后没有通知 ClientHttpHandler

### 修复方案
1. 在 AgentWebSocketHandler 中添加 ResponseCallback 接口
2. 在 Server 构造函数中将 callback 连接到 clientHandler.onAgentResponse()

### 状态: 已修复 ✓

---

## Bug #3: 打包后 README 未更新界面化使用方式

### 问题描述
- 打包后的 `dist/README.txt` 缺少图形化配置工具的使用说明
- 用户无法了解新增加的 Web 版配置工具

### 根本原因
- 添加 config-tool 模块后，未更新 dist/README.txt

### 修复方案
- 在 dist/README.txt 中添加"方式一：使用图形化配置工具"章节
- 说明访问地址: http://localhost:8888

### 状态: 已修复 ✓

---

## 验证结果
- 集成测试: 7/7 通过
- testServerHealthCheck: PASSED
- testServerNotFound: PASSED
- testChatCompletionsWithoutToken: PASSED
- testChatCompletionsWithInvalidToken: PASSED
- testChatCompletionsWithValidToken: PASSED
- testEmbeddingsWithValidToken: PASSED
- testListModels: PASSED