# Zoufx AI · 代码库地址（工作区索引）

> 本工作区（DSH 会话目录 `E:\ZoufxDemo\ZoufxAIAgent\deepseekharness`）只放**规则、索引与技能**；
> 原型 + 设计/规格文档已移入前端仓库 `ZoufxAIAgent-Web\claudedesign\`，Claude Code 资产在 `ZoufxAIAgent\claudecode\`。
> 记录日期：2026-10-21（2026-10-21 按磁盘实况复核更新）

---

## 一、两个代码库

| 角色 | 路径 | Git 远程 | 分支 |
|---|---|---|---|
| 后端 | `E:\ZoufxDemo\ZoufxAIAgent` | `git@github.com:zoufx822/ZoufxAIAgent.git` | `main` |
| 前端 | `E:\ZoufxDemo\ZoufxAIAgent-Web` | `git@github.com:zoufx822/ZoufxAIAgent-Web.git` | `main` |

两者是**同级兄弟目录**（前端代码里多处按 `../ZoufxAIAgent-Web` 相对引用，别随意挪动）。

## 二、后端 `E:\ZoufxDemo\ZoufxAIAgent`

- **技术栈**：Spring Boot 4.1.0 + Java 21 + Spring WebFlux/Reactor Netty + LangChain4J 1.17.2 + Lombok；SQLite 正本 + Qdrant 派生向量索引
- **版本**：`pom.xml` 为 `0.2.5-SNAPSHOT`（`a6bf181` 已把版本回退一并提交，远端一致）
- **入口**：`src/main/java/com/zoufx/ai/agent/ZoufxAIAgentApplication.java`
- **包结构**（按 LOC）：`memory`(20 文件/1257 行) > `chat`(21/1136) > `vector`(14/683) > `tool`(11/646) > `prompt`(10/542) > `llm`(5/325) > `mood`(3/156) > `base`(5/141)
- **HTTP 入口集中在** `chat/controller/ChatController.java`：
  - `POST /ai/chat`（SSE：`turn_started` / `anchor_created` / `thinking` / `content` / `tool_call` / `tool_result` / `mood` / `error`）
  - `POST /ai/chat/stop` · `GET /ai/features` · `GET /ai/anchors*` · `GET /ai/memory/hot`
- **LLM 双 profile**：`deepseek-v4`（OpenAI 兼容）/ `MiniMax-M3`（Anthropic 兼容），由 `ai.llm.profile.active` 切换
- **配置**：`src/main/resources/application.yml`（profile `local`）；SQLite 在 `./data/sqlite/zoufx-ai.db`，Qdrant 数据在 `./data/qdrant`
- **项目规则文件**：已从仓库根移出，归档于 `claudecode\`——`CLAUDE.md`（架构/WebFlux 红线/注释原则/工作原则）、`.claude/settings.json`（Playwright 截图清理钩子）、`.claude/commands/test.md`（`/test` 流程）、`.claude/skills/tech-stack/SKILL.md`（技术选型备忘）
- **启动依赖**：`docker-compose.yml` 起 Qdrant（gRPC 6334，HTTP 6333）

```powershell
docker compose up -d qdrant          # 先起向量库
mvn spring-boot:run                  # 后端 :8080
```

## 三、前端 `E:\ZoufxDemo\ZoufxAIAgent-Web`

- **技术栈**：Next.js 16（App Router）+ React 19.2.4 + TypeScript 5 + Tailwind CSS v4 + Zustand 5 + shadcn/@base-ui + motion v12 + shiki + streaming-markdown；包管理 **pnpm**
- **版本**：`package.json` `0.1.0`（未纳入 `pom` 那套版本同步规则）
- **后端地址**：`.env.local` → `NEXT_PUBLIC_API_URL=http://localhost:8080`
- **主要文件**（按 LOC）：`app/globals.css`(2293) > `components/chat-window.tsx`(631) > `components/eyes.tsx`(398) > `components/message-item.tsx`(338) > `lib/store.ts`(331) > `hooks/use-chat-stream.ts`(306) > `components/ui/stream-markdown.tsx`(298) > `components/memory-anchor-drawer.tsx`(253) > `lib/chat-stream.ts`(232)
- **架构**：`components/layout.tsx` 三段工作台 = `rail.tsx`（左 56px）+ 主区（`chat-window` + `presence-float`）+ `state-panel/`（右 280px，四个子面板：用户印象 / 记忆锚点 / 事件 / 承诺）
- **消息不持久化**：localStorage 键 `zoufx-chat-sessions` 只存 `userId / anchors / currentAnchorId`，切锚时 `useAnchorMessages` 从后端拉窗口消息
- **项目规则文件**：本仓库 `CLAUDE.md` 已于 2026-10-21 删除；规则看后端仓库内 `deepseekharness\AGENTS.md`（DSH）或 `claudecode\CLAUDE.md`（归档）

```powershell
pnpm dev                             # 前端 :3000
```

相关目录：`app/` `components/` `hooks/` `lib/` `tests/`（Playwright 目录，当前为空）、`claudedesign/`（原型 + 设计文档）、`.env.local`、`components.json`（shadcn）

## 四、资产与代码库的对应关系

原型与设计文档不再单独占一个工作区，已按归属并入两个仓库（Claude Design 导出的 handoff bundle 原样保留）：

| 资产（现位于 E 盘各仓库） | 对应的代码库位置 |
|---|---|
| `ZoufxAIAgent-Web\claudedesign\project\Zoufx AI.html`（单文件 React 原型） | 前端 `components/` + `app/globals.css` 已经落地还原 |
| `ZoufxAIAgent-Web\claudedesign\project\uploads\拟人化AI Agent-v0.xx.md`（产品/技术规格） | 后端 `com.zoufx.ai.agent.*` 各模块 |
| `ZoufxAIAgent-Web\claudedesign\project\CLAUDE_CODE_交接_*.md`（逐轮交接提示词） | 前后端两侧的落地改动说明 |
| `ZoufxAIAgent-Web\claudedesign\project\design_handoff*/`（设计 token / 布局 / 数据契约） | 前端样式与右栏数据契约 |
| `ZoufxAIAgent\claudecode\`（`CLAUDE.md` + `.claude/`） | Claude Code 时代的规则/命令/技能归档，只读参考 |
| `ZoufxAIAgent\deepseekharness\`（本目录） | DSH 的规则 `AGENTS.md`、索引 `PROJECTS.md`、技能 `.dsh/skills/` |

`claudedesign/` 是 Claude Design 的导出包（`README.md` 仍是导出自带的说明，里面写的 `zoufx-ai/project/...` 指的就是本目录下的 `project/`）。其中 `_inspect/` 是历史截图与探针产物，可随时清掉。

原型的 v0.2.x 编号 == 代码库的迭代编号（后端 `pom.xml` 版本、前端提交信息一致）。原型上的新改动要**手工平移**到前端代码库，两者不是自动同步的。

## 五、当前状态快照（2026-10-21 复核）

- 后端 `main` 最新提交：`446eba3`（2026-10-21）chore: 归档补入 .claude/settings.local.json —— 已推送，本地与 `origin/main` 一致
- 前端 `main` 最新提交：`6c978b0`（2026-10-21）chore: 移除 CLAUDE.md —— 已推送，本地与 `origin/main` 一致
- 后端工作区：干净，仅剩未跟踪的 `deepseekharness/`（本 DSH 目录）；`pom.xml` 为 `0.2.5-SNAPSHOT`
- 前端工作区：干净，仅剩未跟踪的 `claudedesign/`（原型 + 设计文档，约 6.6 MB）
- 服务均未启动：8080 / 3000 / 6333 / 6334 无监听，Docker daemon 未运行
- 存量数据：`data/sqlite/zoufx-ai.db` 5.7 MB；`data/qdrant/collections/memory_vectors` 存在（上次运行留下的索引）

## 六、注意事项

1. **不要动** `claudedesign/project/Zoufx AI (xx备份).html` 这类原型备份；代码库的 git 提交/推送需用户明确下令，只读 git 命令不受限。
2. 后端 WebFlux 红线：Controller/Service 内禁止阻塞 API（JDBC / RestTemplate / Thread.sleep / 同步 IO）。
3. 代码库文档有滞后处：Skill 里的技术栈版本以 `pom.xml` / `package.json` 为准（`.dsh/skills/tech-stack/SKILL.md` 已于 2026-10-21 校正为 Spring Boot 4.1.0 / LC4J 1.17.2；归档的旧副本 `ZoufxAIAgent\claudecode\.claude\skills\tech-stack\SKILL.md` 仍写 4.0.6 / 1.11.0，别照抄）。
4. `claudedesign/project/打磨清单_TODO.md` 也严重滞后（多项其实已在代码库落地），以代码和最新交接文档为准。
5. 后端仓库已于 2026-10-21 清理 AI 工具残留：删掉 `.codegraph`（代码图索引）、`.playwright-mcp`（测试脚本 + 消息快照）、`.reasonix`（桌面端状态）与空目录 `.worktrees`，约 8.3 MB；`.gitignore` 同步从 51 行精简到 31 行——去掉上述已删目录的规则和 BlueJ / J2ME / Node 模板噪声，保留 `/data/`、`application-local.*`、`.env*`、`target/`、`*.class`、`*.log`、`.idea/`、`.playwright-mcp/`（后者仍被 `/test` 流程使用）。以上改动已随 `a6bf181` 提交。
6. 后端仓库换行符已于 2026-10-21 全库统一为 CRLF：105 个被跟踪文本文件全部 CRLF（原 76 LF / 29 CRLF 混合），内容零改动。注意该仓库 `core.autocrlf=true` + `.gitattributes` 为 `* text=auto`，批量改 EOL 后 `git status` 会误报一批「已修改」，需 `git add --renormalize .`（或排除真实改动文件）刷新索引 stat 缓存才会恢复正常。`git ls-files --eol` 现在全部是 `i/lf w/crlf`。
7. IDEA 2026.2 全局设置 `%APPDATA%\JetBrains\IntelliJIdea2026.2\options\filetypes.xml` 的「Ignored Files and Folders」里曾含 `.gitignore`，导致该文件在**所有项目**的项目树里都不显示、也不进提交面板。2026-10-21 已解决（该项已从忽略列表移除），`.gitignore` 恢复显示。
8. 2026-10-21 删掉了两个仓库的 `.idea` 并让 IDEA 重建。结果：**后端**已重建为干净的 Maven 工程（`ExternalStorageConfigurationManager=true`，模块名 `ZoufxAIAgent`，项目内不再有任何 `.iml`）；**前端**未单独重建（没有自己的 `.idea`），仅以后端项目附加模块的形式挂载，模块描述文件 `ZoufxAIAgent-Web.iml` 位于前端仓库根目录（已加 `*.iml` 忽略）。Git 映射无需手动添加：`vcs.xml` 里那条 `<项目>` 条目本身覆盖「所有模块的内容根」，前端模块的内容根即 `../ZoufxAIAgent-Web`，因此 IDEA 自动识别到 2 个 git 仓库（在「版本控制 → 目录映射」里显示为 `<项目> 已检测到 2`）。

## 七、DSH 原则 / 技能 / 命令（2026-10-21 建立）

在本工作区按 DSH 的加载约定重建了一套，替代原先由 Claude Code 生成的 `.claude/` + `CLAUDE.md`。**原件已归档**到后端仓库的 `claudecode/`（2026-10-21 由用户移出仓库根），并已随 `e4d6bad` / `d2f3804` / `446eba3` 提交。

2026-10-21 另做了一次合并：前端仓库 `ZoufxAIAgent-Web/CLAUDE.md` 的内容已并入 `claudecode/CLAUDE.md` 的「前端（ZoufxAIAgent-Web）」一节，并同步进本目录 `AGENTS.md`；前端仓库那份原件随后删除（`6c978b0`）。**此后维护两份即可**（Claude Code 看归档那份，DSH 看 `AGENTS.md`）。

| DSH 资产 | 本目录路径 | 来自 Claude Code（归档位置） |
|---|---|---|
| 工作区原则（自动加载） | `AGENTS.md` | `ZoufxAIAgent/claudecode/CLAUDE.md` |
| 技能 `/test` | `.dsh/skills/test/SKILL.md` | `claudecode/.claude/commands/test.md` |
| 技能 `/test-prompt` | `.dsh/skills/test-prompt/SKILL.md` | `claudecode/.claude/commands/test-prompt.md` |
| 技能 `tech-stack` | `.dsh/skills/tech-stack/SKILL.md` | `claudecode/.claude/skills/tech-stack/SKILL.md` |

DSH 的加载规则（读自 app.asar 内的包文档，非推测）：

- **指令文件**：`AGENTS.md` / `CLAUDE.md`（含 `.local.md` overlay），从 `.git` 所在项目根向 cwd 逐级加载；用户级为 `~/.dsh/AGENTS.md` → `~/.agents/AGENTS.md`。内容重复的只渲染一次。本会话 cwd = `deepseekharness\`，项目根解析为后端仓库，因此后端根（无 AGENTS.md）与本目录两层都会被扫到。
- **技能根优先级**：`<项目根>/.dsh/skills`(100) > `<项目根>/.agents/skills`(200) > custom(300) > `~/.dsh/skills`(400) > `~/.agents/skills`(500)。cwd 下的 `.dsh/skills` 也生效——本会话三个技能就是从本目录加载的。
- **技能 frontmatter**：`name`（kebab-case，输入框里用 `/name` 调用）、`description`、可选 `whenToUse`；调用控制用 **`disable-model-invocation`**（`true` = 模型不能自动调，只留给人敲 `/name`）和 **`user-invocable`**（`false` = 不出现在 `/` 菜单）。
  ⚠️ **`modelInvocable` / `userInvocable` / `disableModelInvocation` 是非法键** —— 文件加载器把这三个当 legacy 直接抛错，整个技能文件被静默忽略（不报错，只是从目录里消失），排查时极难发现。
- **EOL 用 LF**：frontmatter 解析接受 CRLF，但保持 LF 可避免混行带来的意外。

未迁移的两项（DSH 无对应机制）：

- `claudecode/.claude/settings.json` 里的 `SubagentStop` / `SessionStart` 钩子（原用于自动清 `.playwright-mcp/`）→ 改为一律写 `.dsh/tmp/`、跑完手动清
- `claudecode/.claude/settings.local.json` 的命令权限白名单 → 由 DSH 自身审批策略接管

### 当前待处理（2026-10-21）

- `deepseekharness/`（后端仓库内）与 `claudedesign/`（前端仓库内）都**未被 git 跟踪**，会一直出现在两侧 `git status` 里。两种处理方式：加进各自 `.gitignore`，或把目录移出仓库——等你决定，我不自行动手。
- `claudedesign/project/_inspect/` 是历史截图/探针产物（约几十张 png），需要时单独清理，不影响原型与交接文档。
- 历史项已闭环：原「6 个删除未提交 + `claudecode/` 未跟踪」已由 `e4d6bad` / `d2f3804` / `446eba3` 三个提交处理完。
