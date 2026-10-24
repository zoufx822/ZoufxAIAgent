# AGENTS.md · 工作区规则

> 由 Claude Code 时代的 `CLAUDE.md` 平移而来；原件（含 `.claude/`）归档在 `E:\ZoufxDemo\ZoufxAIAgent\claudecode\`，2026-10-21 起冻结。
> 本文件位于 `E:\ZoufxDemo\ZoufxAIAgent\deepseekharness`（后端仓库内，已跟踪），随会话全局加载。

## 代码库

| 角色 | 路径 |
|---|---|
| 后端 | `E:\ZoufxDemo\ZoufxAIAgent`（Spring Boot + LangChain4J） |
| 前端 | `E:\ZoufxDemo\ZoufxAIAgent-Web`（Next.js 16） |
| 归档 | `ZoufxAIAgent\claudecode\`（冻结）、`ZoufxAIAgent-Web\claudedesign\`（原型） |

技能在 `.dsh/skills/`：`/test`、`/test-prompt`、`tech-stack`。

## 文档分工

| 文档 | 只负责 |
|---|---|
| `AGENTS.md` | 规则、红线、工作原则 |
| `PROJECTS.md` | 仓库地图、启动方式、**API/SSE 契约** |
| `DESIGN.md` | 产品定位、情绪/对话/记忆的**为什么** |
| `DATA-MODEL.md` | 表、字段、索引、写入路径 |
| `TODO.md` | 做什么、优先级、验收 |

实现细节（像素、时长、枚举值、模型名）以代码为准，文档不复述。

## 文档更新触发

| 改动 | 更新 |
|---|---|
| SSE 事件 / REST 字段 | `PROJECTS.md` 契约段 |
| 表 / 字段 / 索引 | `DATA-MODEL.md` |
| 情绪语义 / 产品原则 | `DESIGN.md` |
| 范围 / 优先级 | `TODO.md` |
| 规则 / 红线 | `AGENTS.md` |

## 项目一句话

Spring Boot 4.1 + LangChain4J 1.17 + WebFlux；SQLite 正本 + Qdrant 向量索引；双 LLM profile（`deepseek-v4` / `MiniMax-M3`）。情绪由独立分类器识别用户消息，映射为 `presence`；正文不携带情绪标记。

## 硬约束

- **WebFlux 红线**：Controller/Service 禁止 JDBC、`RestTemplate`、`Thread.sleep`、同步文件 IO；会卡死 event loop。
- **Mono 收口**：`Mono.fromCallable/fromRunnable + subscribeOn` 不手写，统一走 `base/support/Blocking`；只允许在 store 的 `xxxAsync` 或 service 阻塞流水线最外层出现。Controller 不构造 Mono 包装。
- **契约同步**：SSE/REST 改动必须前后端同改；契约以 `PROJECTS.md` 为准。
- **LC4J 边界**：`@Tool` 在框架工具线程跑，不在 event loop；`ChatMemoryStore` 是同步契约，靠 write-back 缓冲。

## 注释原则

- 写设计意图，不写版本变迁；两行说清类/方法职责，删掉结构性描述。
- 保留非显而易见约束（线程契约、编译期常量、fail-fast、seed 语义）；预留列标 `预留：<用途>`。
- 删除版本号、`详见 xxx.md`、选型论证、迁移路径。
- yml 与 `@ConfigurationProperties` 一一对应；文案默认值放 Java 字段，yml 只留阈值/开关/环境变量。

## 代码质量

- 改功能时回看相邻/上游代码，发现过时写法一并改或明确列出。
- 设计有异味先解决根因（归属、职责、多余参数），不用变通拖债。
- 抽函数/加结构 ≠ 优雅：只有降低阅读成本才抽。
- 跨层/跨仓库改动先想清终态和影响面。
- 加字段/参数先问必要性：能从已有状态推导就不新增。

## 工作原则

- 目标不清晰时停下来讨论，不做假设。
- 临时文件按需清理，用户要求时执行。
- 新功能完成后执行 `/test`。
- 测试需要起服务时（Docker/Qdrant、`mvn spring-boot:run`、`pnpm dev`）直接启动，无需申请。
- 版本号变更时同步 `pom.xml`（`major.minor.patch-SNAPSHOT`）。
- `git commit` / `push` / PR 等写操作等用户明确下令；只读 git 不受限。
