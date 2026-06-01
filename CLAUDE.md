# CLAUDE.md

## 项目

Spring Boot 4.0.6 + LangChain4J 1.13.1，支持 `deepseek-v4` / `minimax` 双 LLM profile 切换：DeepSeek 走 OpenAI 兼容协议，MiniMax 走 Anthropic 兼容协议。带会话记忆的流式聊天与思考模式。

Hot Memory 含三种 type（v0.14）：`user-impression`（用户画像，UPSERT）/ `significant-event`（重要经历，append-only）/ `commitment`（双方承诺，append-only）。情绪谱 6 词：平静 / 兴奋 / 难过 / 愤怒 / 好奇 / 困惑。

## 架构

**后端：** Spring WebFlux + Reactor Netty（已移除 `spring-boot-starter-web`，对齐 LangChain4J 官方姿势）。HTTP 入口集中在 `ChatController`：
- `POST /ai/chat`（SSE）—— 单一 ChatAssistant 流式聊天，事件类型 `thinking` / `content` / `tool_call` / `tool_result` / `mood` / `error`
- `GET /ai/capabilities` —— 当前 profile LLM 能力声明（thinkingToggle / thinkingBudget / reasoningEffort）
- `GET /ai/memory/hot?userId=X&type=Y` —— Hot Memory snapshot

配置见 `application.yml`。

**前端：** 独立仓库 `../ZoufxAIAgent-Web`（与后端同级），开发命令 `pnpm dev`（localhost:3000）。启动时拉 `/ai/capabilities` 缓存到 zustand store，UI 行为按 capability 自适应。

## LLM Profile 切换

当前激活 profile 由 `ai.llm.profile` 决定（v0.135 起替代旧 `ai.llm.provider`）：

- `deepseek-v4`：DeepSeek v4 hybrid 模型，always-on 自适应 reasoning，无 thinking 开关
- `minimax`：MiniMax 模型（M1/M2 等），走 Anthropic 兼容协议，builder 期固定开启 thinking

切换 profile = 改 `application.yml` 一行 `ai.llm.profile` + 重启。每个 profile 独立 `@ConfigurationProperties` 命名空间（`ai.llm.deepseek-v4.*` / `ai.llm.minimax.*`），由 `@ConditionalOnProperty` 路由对应 `XxxConfig` 装配 `chatModel` + `LlmCapabilities` Bean。

> 已知限制：LC4J 1.13.1 langchain4j-anthropic 未提供 `AnthropicChatRequestParameters` per-call 子类，thinking 参数无法 per-call 覆盖。minimax profile 的 `thinkingToggle` 暂声明 false（降级），按钮在所有 profile 下统一仅控显示。详见 `obsidian-vaults/tech/Agent开发/拟人化AI Agent-总设计方案.md` 的"已知技术债"章节。

## 后端开发约束（WebFlux）

- 接口签名随意写：返回 POJO/Map/`Mono`/`Flux` 都行，Spring 自动适配
- **红线**：Controller/Service 里禁止直接调 JDBC、`RestTemplate`、`Thread.sleep`、同步文件 IO 等阻塞 API —— 会卡死 Netty event loop
- 必须用阻塞库时，包 `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` 隔离
- 优先选反应式 SDK：HTTP 用 `WebClient`，DB 用 R2DBC
- 例外：LC4J 的 `@Tool` 方法在框架自己的工具线程跑，不在 event loop，无需特殊处理

## 已知瓶颈

唯一阻塞点：`@Tool` 方法（`TavilySearchTool.search_web`）+ Tavily 同步 HTTP client。LC4J 1.x 的 `@Tool` 不支持 `Mono`/`Future` 返回值，是框架边界。最坏 Tavily 全失败时单次工具调用阻塞约 60s（20s × 3 次重试 + backoff）。低并发场景可接受，不修。

## 注释原则

- **写设计意图，不写版本变迁**：说明当前为什么这样设计（约束、取舍），删掉"v0.12 是 X，v0.13 改为 Y"等历史叙述
- **两行说清类/方法的职责**，删掉"归属 xx 包"、"与 xx 对偶"等可从代码结构直接看出的说明
- **保留**：非显而易见的约束（线程契约、编译期常量限制、fail-fast 不变量）、跨版本遗留问题（如 LC4J 未提供的接口）、seed 语义（已有不覆盖）
- **删除**：版本号标注（`v0.xx`）、设计文档引用（`详见 xxx.md`）、选型论证（"为何不用枚举"但代码已自解释）、迁移路径（"从 X 迁到 Y"）
- yml 配置同理——大段 prompt 文案默认值进 Java `@ConfigurationProperties` 字段初始化，yml 只留阈值/开关/环境变量
- yml 与 `@ConfigurationProperties` 必须保持一一对应：yml 删掉的字段，Properties 类里同步删掉，常量内联到调用处；不在 yml 里出现的值不应出现在 Properties 类里

## 工作原则

- 目标不清晰时停下来讨论，不做假设
- 临时文件按需清理，用户主动要求时执行
- 新功能完成后执行 `/test` 自测
- 版本号变更时，`pom.xml` 的 `<version>` 必须同步更新（格式 `major.minor.patch-SNAPSHOT`，如 v0.12 → `0.12.0-SNAPSHOT`）
- `git commit` / `git push` / 创建 PR 等写操作绝不主动执行，等用户明确发命令再做（只读命令如 `git status` / `git diff` 不受限）