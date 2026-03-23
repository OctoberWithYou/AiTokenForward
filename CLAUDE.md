# 项目规则和约定

## 开发规范

### 1. Bug 修复流程
- **每次发现 Bug 必须记录到 BUGS.md**
- 记录内容: 问题描述、根本原因、修复方案、状态
- **所有影响表现的问题都应当被计入 Bug**,包括:
  - 功能缺陷
  - 性能问题
  - 配置错误
  - 文档错误
  - UI/界面问题
- 修复后必须运行集成测试验证
- 测试命令: `gradle integrationTest`

### 2. 文档同步规则
- **每次代码修改后,必须检查并更新相关文档**
- 文档包括:
  - `README.md` - 项目主文档
  - `dist/README.txt` - 打包后的部署说明
  - `CLAUDE.md` - 项目规则
  - `BUGS.md` - Bug 记录
  - `config-tool/README.md` - 配置工具说明
- **特别重要**: 运行 `gradle build` 打包后,必须检查 `dist/README.txt` 是否已更新

### 3. 集成测试
- 集成测试模块: `integration-test/`
- 运行测试: `gradle integrationTest`
- 测试报告位置:
  - HTML报告: `integration-test/build/reports/integration-tests/index.html`
  - Allure报告: `integration-test/build/allure-report/index.html`
- 使用 JUnit 5 + OkHttp 进行黑盒测试
- 测试包含: 认证测试(401)、API 测试(200, 404)

### 4. 构建命令
- `gradle build` - 编译、测试、打包
- `gradle clean` - 清理所有构建产物
- `gradle integrationTest` - 运行集成测试

### 5. Git 提交规范
- 每次功能/修复完成后提交
- 提交信息包含: 改动内容、问题修复说明

### 6. 新特性与用例看护
- **每个新功能必须添加对应的集成测试用例**
- 测试用例应覆盖:
  - 功能正常场景
  - 边界条件和异常场景
- 集成测试模块: `integration-test/`
- 新功能测试通过后才可合并/提交

### 7. 规范遵守
- 任何代码修改必须遵循现有规范
- 修改前先读取相关规范文档
- 提交前确保所有测试通过

## 已修复 Bug

### Bug #1: 服务器 WebSocket 无法接收消息 ✓ 已修复
- 使用 Java NIO 实现完整的 WebSocket 服务器
- 在 HTTP 端口 + 1 上启动独立的 WebSocket 服务

### Bug #2: Agent 注册失败 ✓ 已修复
- 添加 ResponseCallback 机制连接 Agent 响应到 HTTP 请求

### Bug #3: 打包后 README 未更新界面化使用方式 ✓ 已修复
- 添加了图形化配置工具使用说明到 dist/README.txt

## 技术栈
- Java 17
- Gradle 9
- OkHttp 4.12 (HTTP 客户端)
- Jackson (JSON/YAML)
- SLF4J + Logback (日志)
- JUnit 5 (测试)
- Allure 2.24 (测试报告)