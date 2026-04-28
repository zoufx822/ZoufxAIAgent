# CLAUDE.md

## 项目

Spring Boot 4.0.3 + LangChain4J 1.11.0，通过 Anthropic 兼容接口连接 MiniMax AI，支持带会话记忆的流式聊天和思考模式。

## 架构

**后端：** Spring WebFlux + Reactor Netty（已移除 `spring-boot-starter-web`，对齐 LangChain4J 官方姿势）。`POST /ai/chat`（SSE）按 `thinking` 字段路由到两个 ChatAssistant Bean，SSE 事件类型为 `thinking` / `content` / `tool_call` / `tool_result` / `error`。配置见 `application.yml`。

**前端：** 独立仓库 `../ZoufxAIAgent-Web`（与后端同级），开发命令 `pnpm dev`（localhost:3000）。

## 后端开发约束（WebFlux）

- 接口签名随意写：返回 POJO/Map/`Mono`/`Flux` 都行，Spring 自动适配
- **红线**：Controller/Service 里禁止直接调 JDBC、`RestTemplate`、`Thread.sleep`、同步文件 IO 等阻塞 API —— 会卡死 Netty event loop
- 必须用阻塞库时，包 `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` 隔离
- 优先选反应式 SDK：HTTP 用 `WebClient`，DB 用 R2DBC
- 例外：LC4J 的 `@Tool` 方法在框架自己的工具线程跑，不在 event loop，无需特殊处理

## 已知瓶颈

唯一阻塞点：`@Tool` 方法（`TavilySearchTool.search_web`）+ Tavily 同步 HTTP client。LC4J 1.x 的 `@Tool` 不支持 `Mono`/`Future` 返回值，是框架边界。最坏 Tavily 全失败时单次工具调用阻塞约 60s（20s × 3 次重试 + backoff）。低并发场景可接受，不修。

## 工作原则

- 目标不清晰时停下来讨论，不做假设
- 临时文件按需清理，用户主动要求时执行
- 新功能完成后执行 `/test` 自测

**跳过以下 skill**

- `superpowers:test-driven-development`
- `superpowers:brainstorming`
- `superpowers:requesting-code-review`
- `simplify`（用户主动要求时除外）
- `frontend-design:frontend-design`（整体重设计时除外）
