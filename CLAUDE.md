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
- 测试报告位置:
  - HTML报告: `integration-test/build/reports/integration-tests/index.html`
  - Allure报告: `integration-test/build/allure-report/index.html`
- 使用 JUnit 5 + OkHttp 进行黑盒测试
- 测试包含: 认证测试(401)、API 测试(200, 404)

### 3. 构建命令
- `gradle build` - 编译、测试、打包
- `gradle clean` - 清理所有构建产物
- `gradle integrationTest` - 运行集成测试

### 4. Git 提交规范
- 每次功能/修复完成后提交
- 提交信息包含: 改动内容、问题修复说明

## 已修复 Bug

### Bug #1: 服务器 WebSocket 无法接收消息 ✓ 已修复
- 使用 Java NIO 实现完整的 WebSocket 服务器
- 在 HTTP 端口 + 1 上启动独立的 WebSocket 服务

### Bug #2: Agent 注册失败 ✓ 已修复
- 添加 ResponseCallback 机制连接 Agent 响应到 HTTP 请求

## 技术栈
- Java 17
- Gradle 9
- OkHttp 4.12 (HTTP 客户端)
- Jackson (JSON/YAML)
- SLF4J + Logback (日志)
- JUnit 5 (测试)
- Allure 2.24 (测试报告)