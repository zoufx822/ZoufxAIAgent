# CLAUDE.md

## 项目

Spring Boot 4.0.3 + LangChain4J 1.11.0，通过 Anthropic 兼容接口连接 MiniMax AI（`MiniMax-M2.7`），支持带会话记忆的流式聊天和思考模式。

## 命令

```bash
mvn spring-boot:run        # 启动（JDK 21，访问 localhost:8080）
mvn clean package          # 打包
```

## 架构

**后端核心流程：** `ChatRequest { prompt, sessionId, thinking }` → `AIChatController` 按 `thinking` 字段选择 model → SSE
流输出 `thinking` / `content` 两类事件。

- `LangChain4JConfig`：注册 `thinkingChatModel` / `nonThinkingChatModel` 两个 Bean
- `ChatMemoryServiceImpl`：`ConcurrentHashMap` 内存会话，历史以 `"User:..."\n"Assistant:..."` 字符串拼接入 prompt（非
  LangChain4J 原生 Memory API）；换 Redis 只需新建实现类

**前端核心流程：** SSE 字节流 → `parseSSE()` 手动解析 → 流式期间打字机纯文本，完成后 `MarkdownRenderer` 渲染 Markdown +
Prism 高亮。

- `api.js`：fetch + 手动 SSE 解析，含错误分类
- `ChatWindow.js`：打字机效果、thinking 流式期间自动展开
- `MarkdownRenderer.js`：markdown-it + Prism.js，含增量渲染缓存
- `sessionId`：来自 `store.currentSessionId`（内存，刷新重置）

配置：`src/main/resources/application.yml`（API Key、模型、thinking budget）

## 工作原则

**第一性原理**

- 从问题本质出发，不套惯例模板
- 目标不清晰时停下来讨论，不做假设
- 路径不是最短时直接说，并给出更好的办法
- 追根因不打补丁，每个决策能回答"为什么"
- 只说影响决策的信息

**开发**

- 代码写完后不自动提交，告知变更后等用户决定
- 修改后运行测试套件，完成前修复所有失败
- 测试后清理脚本、截图等临时文件

**自测**：新功能开发完成后执行 `/test` 进行自测。

**多 Agent**（同时满足：涉及多个独立模块 + 任务量大）

1. `superpowers:using-git-worktrees` 创建工作树
2. 多 agent 并行开发
3. `superpowers:finishing-a-development-branch` 合并

**跳过以下 skill（本项目不适用）**

- `superpowers:test-driven-development`：项目无测试基础设施，强制先写测试会阻塞开发
- `superpowers:brainstorming`：简单改动无需走完整 brainstorming 流程
- `superpowers:requesting-code-review`：小改动无需触发 code review agent
- `simplify`：代码整理只在用户主动要求时执行，不自动触发
- `frontend-design:frontend-design`：项目已有完整 UI 设计，局部调整时跳过，仅整体重设计时使用
