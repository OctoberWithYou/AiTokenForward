# 项目规则和约定

## 开发规范

### 1. Bug 修复流程
- 每次发现 Bug 必须记录到 BUGS.md
- 记录内容: 问题描述、根本原因、修复方案、状态
- 修复后必须运行集成测试验证
- 测试命令: `gradle integrationTest`

### 2. 集成测试
- 集成测试模块: `integration-test/`
- 运行测试: `gradle integrationTest`
- 测试报告位置: `integration-test/build/reports/integration-tests/index.html`
- 使用 JUnit 5 + OkHttp 进行黑盒测试
- 测试包含: 认证测试(401)、API 测试(200, 404)

### 3. 构建命令
- `gradle build` - 编译、测试、打包
- `gradle clean` - 清理所有构建产物
- `gradle integrationTest` - 运行集成测试

### 4. Git 提交规范
- 每次功能/修复完成后提交
- 提交信息包含: 改动内容、问题修复说明

## 已知 Bug

### Bug #1: 服务器 WebSocket 无法接收消息 (待修复)
- **问题**: WebSocket 握手后没有读取帧的逻辑
- **根本原因**: `AgentWebSocketHttpHandler.handle()` 只处理 101 响应，未启动读取线程
- **修复方案**: 在握手后启动线程读取 WebSocket 帧
- **状态**: 待修复

### Bug #2: Agent 注册失败 (待修复)
- **现象**: 返回 503 "No available agents"
- **原因**: Agent 连接后注册消息未正确传递到 handleRegister
- **依赖**: Bug #1
- **状态**: 待修复

## 技术栈
- Java 17
- Gradle 9
- OkHttp 4.12 (HTTP 客户端)
- Jackson (JSON/YAML)
- SLF4J + Logback (日志)
- JUnit 5 (测试)