# Bug 修复清单

## Bug #1: 服务器 WebSocket 无法接收消息

### 问题描述
- 服务器 WebSocket 实现不完整
- Agent 连接成功后，注册消息无法被服务器接收
- `handleOpen` 被调用，但 `handleMessage` 从未被调用
- 导致 Agent 未被正确注册到 AgentManager

### 根本原因
Server.java 中 `AgentWebSocketHttpHandler` 只处理了 WebSocket 握手(升级协议)，在发送 101 响应后没有：
1. 获取底层 socket 连接
2. 启动线程读取 WebSocket 帧数据
3. 调用 `agentHandler.handleMessage()` 处理接收到的消息

### 修复方案
在 WebSocket 握手后启动一个线程来读取 WebSocket 帧数据。需要解决 Java 21 中无法直接获取底层 socket 的问题。

### 状态: 待修复

---

## Bug #2: Agent 注册失败 (由 Bug #1 导致)

### 问题描述
- 集成测试返回 503 "No available agents"
- 日志显示 "Agent connected" 但没有 "Agent registered successfully"

### 根本原因
与 Bug #1 相关 - Agent 的注册消息从未到达 handleRegister 方法

### 修复方案
修复 Bug #1 后自动解决

### 状态: 待修复 (依赖 Bug #1)

---

## Bug #3: Java 21 反射限制

### 问题描述
- Java 21 中 `com.sun.net.httpserver` 内部 API 访问受限
- 无法通过反射获取 `HttpConnection` 或底层 `Socket`

### 状态: 待修复 (作为 Bug #1 的一部分)

---

## 已完成的修复

(暂无)