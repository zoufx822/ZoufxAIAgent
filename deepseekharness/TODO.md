# TODO · Zoufx AI（2026-10-21）

> 状态：🟥 待讨论 / 🟨 方案已定 / 🟩 进行中 / ✅ 完成
> 依据：代码实况 + `E:\obsidian-vaults\tech\Agent开发` 文档 + 2026-10-21 四轮联调观察
> 说明：后端项目 `E:\ZoufxDemo\ZoufxAIAgent`，前端 `E:\ZoufxDemo\ZoufxAIAgent-Web`

---

## 0. 表情位置（🟥 待选型）

**现象**：表情常驻页面最顶部，聊天时注意力在底部消息/输入框，基本注意不到。

**现状（实测几何，1440×900）**
- `.presence-float`：`position: sticky; top: 0`，整条高 **118px**（眼睛 60 + 状态行 + 上下 padding），占视口 13%
- 布局：presence 0–118 → 滚动区 118–764 → 输入区 764–900
- 设计文档 v0.145 §3.2/§3.3 规定两态：collapsed（Eyes 32px / 高 ~56px，`(eyes) · 情绪 · 状态`）/ spotlight（Eyes 88px / 高 ~170px，光晕 + 弹缩 + 文字 rise，**mood 值变化触发、3s 回收**）——**当前两态都没实现**，常驻 118px
- 情绪光晕 380×200，实际视觉比眼睛大一圈

**注意：文档的 spotlight 机制本身就是"解决注意不到"的设计**——每次情绪变化时表情放大到 88px 持续 3s。它和"位置"是两个独立问题，建议一起做。

**候选方案**
- A. 停靠输入框上方（推荐）：眼睛缩到 ~38px + 状态行内联，贴着打字焦点；滚动区多出 100px 以上
- B. 顶部右侧紧凑条：眼睛 32px + 状态横排，整条压到 46px，保留"顶部存在感"
- C. 聊天列右侧中部悬浮：固定 right≈304px / top 46%，不占纵向空间，靠余光捕捉（需修 label 换行）
- D. 跟随最新消息：表情挂在最后一条 AI 消息尾部，注意力天然所在（实现成本最高）
- E. 双 presence：顶部保留大表情 + 输入框旁小镜像（信息重复，先不选）

**待定**：选 A/B/C/D 之一；A+C 可做「滚动到顶→顶部大表情，滚到底→输入框小表情」的联动。**另需决定是否同时实现 v0.145 的 collapsed/spotlight 两态**（document 既定设计，目前缺失）。

---

## 1. 持久层换 MySQL + R2DBC（🟨 方案已定，P0）

**目标**：MySQL 9 跑在本地 Docker Desktop；持久层 SQLite/JdbcTemplate → R2DBC；不迁数据，**清空 SQLite + Qdrant 全部数据**。

**现状**
- `MemoryDataSourceConfig`：手写 HikariCP(max=5) + `memoryJdbcTemplate` + `memoryTxTemplate`，URL `jdbc:sqlite:`，`PRAGMA journal_mode=WAL`
- 5 个 DaoImpl 各自 `@PostConstruct` 建表，无 schema.sql / Flyway
- 表：`anchor_memory` / `chat_memory` / `cold_memory` / `hot_memory` / `soul_profile`；时间全 BIGINT millis；`INSERT OR IGNORE`、`ON CONFLICT ... excluded.*`、`AUTOINCREMENT`、`last_insert_rowid()`、`PRAGMA table_info`
- pom：`spring-boot-starter-jdbc` + `sqlite-jdbc`，无 r2dbc / mysql / flyway

**改造清单**（以下按 `v0.2.5-开发方案` §二–§五 执行，已逐字核对）
1. compose 加 `mysql:9` 服务：`MYSQL_ROOT_PASSWORD`、`MYSQL_DATABASE=zoufx_ai`、`utf8mb4`、volume `./data/mysql`
2. pom：+`spring-boot-starter-data-r2dbc`、+`io.asyncer:r2dbc-mysql`（BOM 托管 1.4.2，不写版本）；−`sqlite-jdbc`、−`spring-boot-starter-jdbc`（HikariCP 随之移除，`MemoryDataSourceConfig` 必须一起删）
3. `application.yml`：`spring.r2dbc.url/username/password` + `spring.sql.init.mode: always`；删 `ai.memory.db-path` 与 `MemoryProps` 字段
4. DDL：集中 `schema.sql`（`CREATE TABLE IF NOT EXISTS`，由 `R2dbcScriptDatabaseInitializer` 执行）；删 5 处 `@PostConstruct` 建表与全部存量迁移代码（columnExists / DROP COLUMN / RENAME / FTS 清理 / WAL）
5. 方言改写（文档 §3.1 已 spike 实测）：
   - id/枚举列 `TEXT` → `VARCHAR(36/64)`；正文 `MEDIUMTEXT`（**不是 LONGTEXT**，文档明确 16MB 足够）
   - 自增 `INTEGER` → `BIGINT AUTO_INCREMENT`
   - 时间保持 `BIGINT` epoch millis，Java 侧零改动
   - soul seed `INSERT OR IGNORE` → `INSERT IGNORE`（文档已实测幂等；**不要**改成 ON DUPLICATE KEY）
   - hot upsert `ON CONFLICT ... excluded.*` → `INSERT ... AS new ON DUPLICATE KEY UPDATE`（`VALUES()` 自 8.0.20 弃用）
   - `last_insert_rowid()` → `Statement.returnGeneratedValues("id")`（同 statement 返回，原"内层事务双保险"删除）
   - `key` 是保留字：`hot_memory.key`、`soul_profile.key` 全部反引号，**不改列名**
6. 响应式改造：5 个 Dao 接口 + 调用方全改 `Mono/Flux`；`xxxAsync` 后缀退役（`Blocking.call` 包装删除）；`Blocking` 工具保留给剩余阻塞 SDK（embedding / syncChatModel / Qdrant / Tavily）
7. 编排层全反应式（文档强调：**只换 DAO 不换编排等于白换**）：
   - `prepare` 改反应式链；**幽灵 pending 双向探测语义逐条对齐**（doOnCancel 先置位再读引用 / register 后 flatMapMany 前的取消窗口）
   - `persistTurn`：`TransactionTemplate` → `TransactionalOperator`；chat → cold×2 → hot → anchor 串链 `.as(txOp::transactional)`，成功后 `then` 接 Qdrant 索引（向量绝不先于正本提交）
   - **事务链内禁止混入 `Blocking.call`**（会丢 Reactor Context，事务静默失效）
   - `ChatMemoryService.seed` 返回 `Mono`；`getMessages` 缺失 pending 改 **fail-fast**（删 DB fallback）
   - `ChatScheduler` 压缩链、`BackfillRunner` 同步收口
8. **硬约束**：LC4J 1.17.2 `ChatMemoryStore` 是同步契约，`chat_memory` 无法端到端响应式 → 靠 write-back 内存缓冲，turn 内不碰 DB（文档 §5.3 已有设计）
9. 清库：`docker compose down` → 删 `./data/sqlite/`、`./data/qdrant/`（约 482MB）→ up → 首启建表 + soul seed，`EmbeddingConfig` 自动重建空 collection
10. 收尾：更新 CLAUDE.md / AGENTS.md 的 SQLite 表述与 Mono 收口规范（适用面收窄）；tech-stack 技能补一行 MySQL 9 + R2DBC；pom 版本保持 `0.2.5-SNAPSHOT`（文档已定，**不升 0.3.0**）

**风险**
- `ChatMemoryStore` 同步契约是最容易破 WebFlux 红线的地方
- `chat_memory.id` 会被当作 Qdrant sourceId → 清库必须 MySQL 与 Qdrant 同时清，否则确定性 pointId 撞车、召回串正文
- R2DBC null 绑定要 typed null（`Parameter.empty(String.class)`）
- 大小写不敏感排序规则（`utf8mb4_0900_ai_ci`）会让 id 比较变松，id 列建议 `_as_cs`/`_bin`

**验收**：五表建表成功；四轮对话可跑通（工具调用、情绪、记忆召回）；重启后数据仍在；无阻塞调用出现在 event loop；Qdrant 为空后重建成功。

**参考**：Obsidian `拟人化AI Agent-v0.2.5-开发方案.md` 已写过同一套设计，可直接对照。

---

## 2. 情绪「一闪而过的发怒 → 愉快」（🟥 P1）

**根因（已定位，无分类器，纯 LLM 标记驱动）**
1. `ExpressionPromptImpl` 要求「开头必打一个情绪」+「转折再追加（可多个）」，示例还把两个标记并排 → 同一轮连发很常见
2. 后端 `MoodEventProcessor` 见一个标记就发一条 SSE `mood`，不做时间间隔控制
3. 前端 `use-mood-presence` 里 `MOOD_MIN_MS=1500` 只压「思考中」占位脸，**不锁 mood 本身**；第二个情绪立即抢占
4. 表情是硬切（愉快走另一分支并 `display:none` 普通眼），0.26s 入场也救不了几十 ms 的停留
5. 跨轮残留：全仓没有 `setMood(null)`，新一轮首标记到达前沿用上一轮情绪
6. 后端兜底：整轮零合法标记时补发「愉快」

**方案（候选，可组合）**
- 前端情绪队列 + 最小停留（建议 1500–2000ms），下一情绪排队而非抢占；切换加 250–350ms 交叉淡入
- `turn_started` 时重置情绪（或至少标记 stale），避免跨轮残留
- 提示词收紧：同一轮最多 2 个标记，两个标记之间至少隔两句；短回复只打开头一个
- `MOOD_MIN_MS` 改为真正锁 mood（而不是只锁 thinking）

**验收**：构造"先愤怒后愉快"的输入，两个表情各自可见 ≥1.5s；快速连发 3 个情绪不闪烁；刷新/新一轮不残留上一轮情绪。

---

## 3. 说话太罗嗦（🟥 P1）

**根因**
- `ExpressionPromptImpl` 的「回复框架」是四段式（承接→回应→关联→推进），容易被当成模板执行
- `UserImprPromptImpl` 在 stranger 档要求"首次交谈必须补问称呼"、每 2–3 轮找话口了解 → 回复尾部长出追问
- SOUL 只有一句"信息密度高于装饰，回答尽量精炼"，**没有字数/段数硬约束**
- 实测：概念问题 326 字、记忆问题 101 字，结构常为"标题 + 三个加粗小段"

**方案（候选）**
- 回复框架改成"默认先给结论，1–3 段；只有用户要展开/问题复杂才分层"
- 给出显式长度预算（如闲聊 ≤120 字、解释 ≤250 字），并把"推进"从默认动作改为可选
- 了解对方的追问限制在"用户主动给信息"或自然话口，禁止每轮结尾都问
- 用 `/test-prompt` 做正反用例验证，避免改完变冷淡

**验收**：同一组 10 个日常问题，平均字数下降 30%+ 且信息不丢；连续 3 轮不出现"每轮都追问"。

---

## 4. 用户印象工具调用合并（🟨 P1）

**现象**：首轮"你好，我叫阿泽…"触发 3 次 `update_user_impression`（username / language / role），每次都带完整上下文回 pro 模型再推理，成本≈3 倍。

**方案**：工具入参改批量（一次传多个字段），或后端提供 `upsert_profile(map)`；提示词同步改为"一次写全"。

**验收**：首轮工具调用 ≤1 次，画像字段全部落库。

---

## 5. 刷新后信息丢失（🟨 P2）

**现象**
- `chat_memory` 只存 `content + mood`，**thinking 不落库**；刷新后思考块消失
- `GET /messages` 返回 13 条（含 `system`、`tool_execution_result`），但前端只渲染 user/assistant，工具卡消失
- 情绪回落到默认「平静」，即使锚点 `lastMood=愉快`（前端加载时未消费）

**方案（候选）**
- `chat_memory` 增 `thinking` 列并回传；或明确"刷新后不展示思考"的产品决策
- `GET /messages` 过滤 system/tool 行或返回结构化 toolCalls；前端按需渲染
- 加载锚点时用 `anchor.lastMood` 初始化情绪（注意 5min 衰减规则）

**验收**：刷新后思考/工具卡/情绪与刷新前一致（或按产品决策一致地隐藏）。

---

## 6. 思考档位（🟨 P2）

- 官方当前支持 `low/high/max`，项目只暴露「标准(high)/极致(max)」，`low` 未暴露
- 实测「标准」档首轮问候思考 4356 字（英文推理），回复仅 87 字；「极致」档反而 1492 字 → effort 与思考量非线性
- 方案：把 `low` 暴露为「轻量」档，或按"消息长度/意图"自动降档；同时给 thinking 加预算上限

**验收**：同一句寒暄，标准档思考长度下降一个量级，回复质量不降。

---

## 7. `max-tokens: 2048` 偏紧（🟨 P2）

- DeepSeek 两模型均支持 384K 输出，配置只给 2048，长文/长代码会被截断
- reasoning token 不受它约束（思考能到 4k+ 字）
- 方案：按档位配置（快档 2048 / 思考档 8192+），或直接提到 8192 并观察成本

---

## 8. 右栏「锚点」空态文案（🟨 P3）

`/context` 的 near/mid/far 只统计**其他**锚点，单锚点时 total=0 → 显示「这是我们的第一次对话。」，容易被读成"没有锚点"。改为「这是第一条对话，之后会在这里形成记忆」之类。

---

## 9. 思考语言是英文（🟨 P3）

DeepSeek reasoning 稳定输出英文；当前前端不展示思考所以无感，一旦恢复（见 #5）会出现"内心戏英文"的割裂。可在提示词加一句"思考过程也用中文"，或在展示层做语言标记。

---

## 10. 文档与实现偏差（🟨 P3，记录备查）

- v0.2.5 方案（MySQL/R2DBC）**整体未落地**：pom 仍 sqlite-jdbc，yml 仍 `ai.memory.db-path`
- mood 词表：文档 6 词，代码 `Moods.ALL` 7 词（多「愉快」）
- v0.14 文档称词表在 `ai.soul.mood.keywords`，实际在 `Moods.java` 常量
- v0.2 文档提到"并行瞬时情绪分类"，代码已无此类（纯标记驱动）
- v0.145 的 PresenceSticky collapsed/spotlight 两态未实现（常驻 118px）
- `claudecode/` 归档已冻结，不再同步

---

## 建议顺序

1. **#1 MySQL + R2DBC**（清库动作最先做，避免后续联调数据白造）
2. **#2 情绪闪变**（体验硬伤，改动集中在前端 + 一句提示词）
3. **#0 表情位置**（等选型；与 #2 一起改，避免二次动 presence）
4. **#3 啰嗦**（prompt 调优，可与 #2 并行，用 `/test-prompt` 验收）
5. **#4 工具合并 → #5 刷新恢复 → #6 档位 → #7 max-tokens → #8/#9 文案与思考语言**
