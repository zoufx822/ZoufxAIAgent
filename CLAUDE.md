# CLAUDE.md

## 项目

Spring Boot 4.0.3 + LangChain4J 1.11.0，通过 Anthropic 兼容接口连接 MiniMax AI，支持带会话记忆的流式聊天和思考模式。

## 架构

**后端：** `POST /ai/chat`（SSE）按 `thinking` 字段路由到两个 ChatAssistant Bean，SSE 事件类型为 `thinking` / `content` / `error`。配置见 `application.yml`。

**前端：** 独立仓库 `../ZoufxAIAgent-Web`（与后端同级），开发命令 `pnpm dev`（localhost:3000）。

## 技术栈

### 后端（已在用）

| 技术 | 说明 |
|------|------|
| Spring Boot 4.0.3 | Web 框架，JDK 21 |
| Spring WebFlux | 返回 `Flux<ServerSentEvent>` 实现 SSE 流式输出 |
| LangChain4J 1.11.0 | AiServices 动态代理、ChatMemory、TokenStream |
| langchain4j-anthropic | 连接 MiniMax Anthropic 兼容接口的 StreamingChatModel |
| Lombok | 减少样板代码（`@Slf4j` 等） |

### 前端（已在用）

| 技术 | 说明 |
|------|------|
| Next.js 16 | App Router，SSR/路由 |
| React 19 | UI 框架 |
| TypeScript | 类型安全 |
| Tailwind CSS 4 | 工具类样式 |
| @tailwindcss/typography | Markdown prose 排版样式 |
| @base-ui/react | headless UI 原语（Button / Input / Tooltip / Sheet / ScrollArea 等） |
| shadcn | 组件代码生成器（构建时工具） |
| Zustand 5 | 客户端状态管理（会话列表、消息、加载状态） |
| streaming-markdown | 流式增量解析并直接写入 DOM，实现打字机效果 |
| Shiki | 流结束后对代码块应用语法高亮 |
| Motion | 动画库（消息展开/收起的 AnimatePresence） |
| lucide-react | 图标 |
| next-themes | 深色/浅色主题切换 |
| sonner | Toast 通知（用于流式错误提示） |

### 前端（已安装未实际用上）

| 技术 | 说明 |
|------|------|
| @tanstack/react-query 5 | 服务端状态管理，QueryClient 已配置，无实际查询 |

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
