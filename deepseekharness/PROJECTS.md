# PROJECTS.md · 仓库与契约

> 工作区 `deepseekharness` 只放文档/技能；代码在两个仓库。
> 契约是前后端唯一口径，改一侧必须同步另一侧。

## 一、仓库

| 角色 | 路径 | 远程 |
|---|---|---|
| 后端 | `E:\ZoufxDemo\ZoufxAIAgent` | `git@github.com:zoufx822/ZoufxAIAgent.git` |
| 前端 | `E:\ZoufxDemo\ZoufxAIAgent-Web` | `git@github.com:zoufx822/ZoufxAIAgent-Web.git` |

同级兄弟目录，不要随意挪动。

## 二、后端

- Spring Boot 4.1.0 + Java 21 + WebFlux/Reactor Netty + LangChain4J 1.17.2；SQLite 正本 + Qdrant 派生索引
- 版本 `0.2.5-SNAPSHOT`；注意 0.2.5 的 MySQL/R2DBC 方案**未实施**，代码实际是 0.2.4 + 情绪重构
- 入口 `src/main/java/com/zoufx/ai/agent/ZoufxAIAgentApplication.java`
- 配置 `src/main/resources/application.yml`（`local` profile）；SQLite `./data/sqlite/zoufx-ai.db`，Qdrant `./data/qdrant`

```powershell
docker compose up -d qdrant   # :6333 / gRPC :6334
mvn spring-boot:run           # :8080
```

## 三、前端

- Next.js 16（App Router）+ React 19 + TS 5 + Tailwind v4 + Zustand 5 + @base-ui/shadcn + motion + shiki
- 布局：`layout.tsx` = Rail(56) + 主区（chat-window + presence-float）+ StatePanel(280)
- 状态 `lib/store.ts`：只持久化 `userId / anchors / currentAnchorId`，消息不持久化
- `.env.local`：`NEXT_PUBLIC_API_URL=http://localhost:8080`

```powershell
pnpm dev        # http://localhost:3000
```

## 四、API / SSE 契约

### POST `/ai/chat`（SSE）

请求：

```json
{ "prompt": "...", "anchorId": "uuid|null",
  "thinking": { "enabled": true, "effort": "low|normal|max" },
  "userId": "..." }
```

事件顺序：`turn_started` → `anchor_created`（仅新锚点）→ `presence` → `thinking` / `content` / `tool_call` / `tool_result` / `error`。

| 事件 | data |
|---|---|
| `turn_started` | `turnId` 纯文本 |
| `anchor_created` | `anchorId` 纯文本 |
| `presence` | `{ expression, intensity }`，一轮一次 |
| `thinking` / `content` | 文本增量 |
| `tool_call` | `{ tool, toolDisplay, query }` |
| `tool_result` | `{ tool, toolDisplay, count, resultPreview }` |
| `error` | `{ error, message }` |

**consumeStream**：生成挂后端自持订阅，客户端只是 `Sinks.replay().all()` 视图；断连只取消视图，后端跑完仍落库。

### 其他接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/ai/chat/stop` | `{turnId}` → `{stopped}`；断连≠停止 |
| GET | `/ai/features` | profile + effort 能力 |
| GET | `/ai/anchors?userId=` | 锚点列表，字段含 `lastExpression` |
| GET | `/ai/anchors/{id}/messages` | 窗口消息（role + content） |
| GET | `/ai/anchors/{id}/pending` | 在建轮 `{turnId,prompt}` 或 `{}` |
| GET | `/ai/anchors/{id}/context` | 三层衰减视图 |
| PATCH | `/ai/anchors/{id}/title` | 重命名 |
| GET | `/ai/memory/hot?userId=&type=` | Hot Memory snapshot |

锚点 JSON：`{ id, userId, title, summary, lastExpression, createdAt, lastActiveAt }`。

## 五、归档与资产

| 资产 | 位置 |
|---|---|
| 原型/设计交接 | `ZoufxAIAgent-Web\claudedesign\`（`_inspect/` 是历史截图） |
| Claude Code 资产 | `ZoufxAIAgent\claudecode\`（冻结） |
| DSH 技能 | `deepseekharness\.dsh\skills\` |

## 六、注意

- `application-local.yml`、`.env.local` 含密钥，已被 `.gitignore` 忽略。
- 旧数据库不迁移：换 schema 后删 `data/sqlite` + `data/qdrant` 重建。
- 原型备份不要动；git 写操作等用户下令。
