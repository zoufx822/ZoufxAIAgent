# D 阶段实施方案：数据结构优化 + 代码同步

> 前置：`PLAN.md` §12「先数据层，后情绪层」；表结构语义见 `DATA-MODEL.md`。
> 原则：**本阶段不改变任何情绪行为**——现有 mood 流程照常工作，只换底层存储与命名，保证应用随时可运行、可回滚。

---

## 1. 目标与边界

**做**
- 定稿 5 张既有表的字段（去掉死列/歧义列，补新语义列）
- 同步 DAO、Service、API DTO、前端类型与引用
- 迁移现有 SQLite 数据（保留对话）

**不做**
- 不改 SSE 事件（`mood` 继续发）
- 不改前端表情/情绪逻辑（继续读 `currentMood`）
- 不建 `relation_state`、不改 `cold_memory` 的情绪写入语义（留给 E 阶段）
- 不碰 MySQL / R2DBC

---

## 2. 目标 Schema（D 阶段结束时）

```sql
-- 对话锚点（每个对话一行）
CREATE TABLE anchor_memory (
    id              TEXT    PRIMARY KEY,
    user_id         TEXT    NOT NULL,
    title           TEXT,
    summary         TEXT,
    last_expression TEXT,              -- 原 mood：只存最后一个表情词
    created_at      INTEGER NOT NULL,
    last_active_at  INTEGER NOT NULL,
    summarized_at   INTEGER
);
CREATE INDEX idx_anchor_memory_user_active ON anchor_memory(user_id, last_active_at DESC);

-- LLM 窗口消息（最近 20 条）
CREATE TABLE chat_memory (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    TEXT    NOT NULL,
    anchor_id  TEXT    NOT NULL,
    role       TEXT    NOT NULL,
    content    TEXT    NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX idx_chat_memory_anchor ON chat_memory(anchor_id);

-- 全量对话归档（召回正文来源）
CREATE TABLE cold_memory (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    TEXT    NOT NULL,
    role       TEXT    NOT NULL,
    content    TEXT    NOT NULL,
    emotion    TEXT,                   -- 预留：用户情绪（E 阶段写入）
    intensity  INTEGER,                -- 预留：1-5
    created_at INTEGER NOT NULL
);
CREATE INDEX idx_cold_memory_user_time ON cold_memory(user_id, created_at);
```

`hot_memory` / `soul_profile` 不动；`relation_state` 留到 E 阶段建（避免无调用者的死代码）。

**列变化**
| 表 | 变化 |
|---|---|
| anchor_memory | `mood` → `last_expression`（内容从"轨迹"改为"最后一个词"） |
| chat_memory | 删除 `mood`（死列） |
| cold_memory | 删除 `mood`；新增 `emotion`、`intensity`（本阶段留空） |

---

## 3. 迁移策略（保留现有数据）

SQLite 数据量约 5.9 MB，采用"备份 → 加列 → 回填 → 切代码 → 删旧列"的顺序，任何一步中断都能回滚。

### 3.1 备份
启动前复制 `data/sqlite/zoufx-ai.db` → `data/sqlite/zoufx-ai.db.bak-20261022`（含 `-wal`/`-shm`）。

### 3.2 迁移步骤（在每个 DaoImpl 的 `@PostConstruct` 内，沿用现有 `columnExists` 模式，幂等）

1. **anchor_memory**
   - `CREATE TABLE IF NOT EXISTS` 用新列名（新库直接终态）
   - 旧库：若无 `last_expression` → `ALTER TABLE ADD COLUMN last_expression TEXT`
   - 回填：`SELECT id, mood FROM anchor_memory WHERE last_expression IS NULL AND mood IS NOT NULL`，Java 侧取逗号轨迹最后一个词，逐行 `UPDATE`
   - 回填完成后：若 `mood` 仍存在 → `ALTER TABLE DROP COLUMN mood`（SQLite 3.53 支持；若不支持则走重建表）
2. **chat_memory**
   - 新库：建表时不含 `mood`
   - 旧库：若 `mood` 存在 → `ALTER TABLE DROP COLUMN mood`
   - **保留**剥离逻辑：`content` 落库前仍要剥掉 `⟦mood:...⟧`（否则刷新后标记泄漏），只是不再存 mood 列
3. **cold_memory**
   - 旧库：若无 `emotion` → `ADD COLUMN emotion TEXT`；若无 `intensity` → `ADD COLUMN intensity INTEGER`
   - 若 `mood` 存在 → `DROP COLUMN mood`（历史 AI 情绪轨迹不迁移，保留无意义）
4. 启动日志打印每步结果（added/dropped/backfilled 行数），便于核对

> 幂等性：所有分支先查 `columnExists`，重复启动不重复执行。

### 3.3 回滚
- 代码：`git revert` 本次提交
- 数据：停服务 → 用 `.bak` 覆盖 → 重启

---

## 4. 代码改动清单

### 4.1 后端

| 文件 | 改动 |
|---|---|
| `memory/impl/AnchorMemoryDaoImpl.java` | DDL 用 `last_expression`；迁移/回填；`touch(anchorId, lastExpression)`；查询/映射改名；删 `lastMoodOf` |
| `memory/api/AnchorMemoryDao.java` | `touch` 参数注释/命名改 `lastExpression` |
| `memory/model/AnchorMemory.java` | 字段 `lastMood` → `lastExpression` |
| `memory/impl/ChatMemoryDaoImpl.java` | DDL/INSERT/SELECT 去掉 `mood`；保留标签剥离；旧列 DROP |
| `memory/impl/ColdMemoryDaoImpl.java` | DDL 增 `emotion/intensity`、删 `mood`；`append` 签名加两参 |
| `memory/api/ColdMemoryDao.java` + `memory/service/ColdMemoryService.java` | `append(..., emotion, intensity)` |
| `chat/service/AnchorService.java` | `touch(anchorId, lastExpression)` |
| `chat/service/ChatService.java` | 落库时：`cold append` 传 `null, null`；`anchor touch` 传 `turn.lastExpression()`（本轮轨迹最后一个词，行为不变） |
| `chat/support/Turn.java` | 新增 `lastExpression()`（取 `inlineMoods` 末位），暂留 `moodTrail()` 或删除；E 阶段再整体替换 |
| `chat/model/AnchorContextView.java` | `AnchorSummary.mood` → `lastExpression` |

### 4.2 前端（只改类型/字段名，不改表情逻辑）

| 文件 | 改动 |
|---|---|
| `lib/api.ts` | `Anchor.lastMood` → `lastExpression`；`AnchorSummary.mood` → `lastExpression` |
| `components/memory-anchor-drawer.tsx` | 映射字段改名 |
| `components/state-panel/memory-anchors.tsx` | `a.mood` / `anchor.mood` → `lastExpression` |

> 前端 `currentMood`、`use-mood-presence`、`presence-float`、`eyes` 本阶段**完全不动**。

### 4.3 API 契约

| 接口 | 变化 |
|---|---|
| `GET /ai/anchors?userId=` | 字段 `lastMood` → `lastExpression` |
| `GET /ai/anchors/{id}/context` | `AnchorSummary.mood` → `lastExpression` |
| 其余接口 / SSE | 不变 |

---

## 5. 验证清单（D 阶段）

**迁移正确性**
- 复制一份现有库到临时目录，跑迁移，检查：`PRAGMA table_info` 字段正确；`last_expression` 回填值 = 原轨迹最后一个词；`chat/cold` 的 `mood` 已消失
- 连续重启两次，第二次不再执行迁移（幂等）

**功能回归**
- 启动、四轮对话（思考/快档各一轮）、切锚、刷新、`GET anchors`/`context`、召回、10 分钟压缩扫描
- 刷新后旧对话仍能加载（`chat_memory.content` 未被破坏）

**构建**
- `mvn -DskipTests compile`
- `pnpm build`（前端类型改名后必须过 TS）

**契约**
- `curl /ai/anchors` 确认 JSON 字段为 `lastExpression`，前端右侧「锚点」面板正常

---

## 6. 风险与应对

| 风险 | 应对 |
|---|---|
| 删列导致旧代码路径报错 | 全量 grep `mood`，编译 + 启动即暴露；迁移与代码同一提交 |
| 回填把多词轨迹截错 | 只取最后一个逗号后的词；迁移日志打印样例 |
| SQLite `DROP COLUMN` 不支持/失败 | 检测版本；失败则重建表（CREATE new → INSERT SELECT → DROP old → RENAME） |
| `chat_memory` 里的标记泄漏 | 保留剥离逻辑，只是不存列 |
| 前端漏改字段 | `pnpm build` + 页面实测；grep `lastMood` 清零 |
| 数据丢失 | 迁移前 `.bak`；先在一份拷贝上演练 |

---

## 7. 执行顺序

| 步骤 | 内容 | 产出 |
|---|---|---|
| D1 | 备份 + 三个 DAO 的 DDL/迁移 + 模型/DTO 改名 | 后端可启动，schema 终态 |
| D2 | `ChatService` / `AnchorService` / `ColdMemoryService` 同步 | 对话、摘要、召回行为不变 |
| D3 | 前端类型与字段引用改名 | `pnpm build` 通过 |
| D4 | 回归验证 + 报告 | 截图 / curl / 启动日志 |

预计 1.5–2 天（不含 E 阶段）。

---

## 8. 待确认（开工前）

1. **数据保留**：原地迁移保留现有对话（推荐），还是清库重建？
2. **API 字段**：直接改名 `lastExpression`（推荐，单客户端），还是先加别名 `mood` + `lastExpression` 过渡？
3. **`relation_state`**：放 E 阶段建（推荐，避免死代码），还是 D 阶段一起建好？
