# 数据模型（现状 + 重规划）

> 目标：先把数据结构定稿并同步代码，**再**改情绪。日期：2026-10-22
> 数据库：当前 SQLite（`./data/sqlite/zoufx-ai.db`）；MySQL/R2DBC 已暂缓。

---

## 0. 一张图理解六张表

```
用户
 ├─ soul_profile          AI 自己的人格设定（全局一张，与用户无关）
 ├─ relation_state        我和这个用户的关系温度（每用户一行）
 │
 ├─ hot_memory            关于这个用户的"档案卡"：画像 / 重要经历 / 承诺
 │
 ├─ anchor_memory         对话文件夹的封面：每个对话一行（标题/摘要/最后活跃）
 │    └─ chat_memory      这个文件夹里的"聊天记录窗口"（最近 20 条，给 LLM 看）
 │
 └─ cold_memory           全量日记本：所有对话原文，跨对话，检索用
```

- **anchor_memory 不是消息**，是"对话的目录/封面"。前端左侧/记忆锚点列表读它；系统靠 `last_active_at` 判断哪些对话空闲了、该压缩摘要了。
- **chat_memory 才是消息**，但只保留滑窗（默认 20 条），并且 `content` 是 LC4J 的消息 JSON（含工具调用结构），不是纯文本。
- **cold_memory 是原文归档**，不按对话分，按用户；语义召回和 `search_cold_memory` 都从这里取正文。
- **hot_memory 是精炼后的结论**，不是流水账：画像字段、重要经历、承诺。
- **soul_profile 是 AI 自己是谁**，seed 一次，运行期只读。

---

## 0.1 术语澄清：mood / affect / expression 是三个东西

| 概念 | 现状 | 新方案 | 存在哪 | 谁产生 |
|---|---|---|---|---|
| **AI 自身情绪**（旧 mood） | LLM 在正文里自报 7 词，驱动脸，存成轨迹 | **取消** | — | — |
| **用户情绪**（user affect） | 无 | 每轮 6 词 + 强度 1–5 | `cold_memory.emotion / intensity` | flash 分类器 |
| **显示表情**（expression） | 就等于旧 mood | 由"用户情绪 + 关系状态"派生 | `anchor_memory.last_expression` | 后端规则 |
| **关系状态** | 无 | familiarity / trust / warmth | `relation_state` | 逐轮累计 |

关键语义变化：**眼睛不再显示"AI 自己的心情"，而是显示"对用户的共情反应"**。
- 用户难过 → 显示难过（共情）
- 用户遭遇不公而愤怒 → 显示愤怒（站在对方一边）
- 用户愉快 → 显示愉快
- 情绪不明显 → 用关系基调（平静/愉快）

如果以后确实要"AI 自己的情绪"，也应该由关系状态派生（一个慢变量），而不是每轮再让模型自报一个标签。

---

## 1. anchor_memory（对话锚点元数据）

| 字段 | 类型 | 作用 | 读/写 |
|---|---|---|---|
| id | TEXT PK | 锚点 UUID，前端切对话、拼 URL 用 | 创建时生成 |
| user_id | TEXT NOT NULL | 归属用户 | 查询/索引 |
| title | TEXT | 对话标题（首条消息截断，手动可改） | 列表、抽屉 |
| summary | TEXT | 这个对话的滚动摘要（空闲 1h 后增量压缩） | 注入其他对话的 system prompt |
| mood | TEXT | **现状**：本轮情绪轨迹（"难过,愤怒,愉快"）；API 只取最后一个词 | 前端刷新后恢复表情 |
| created_at | INTEGER | 创建时间（epoch ms） | 排序/展示 |
| last_active_at | INTEGER | 最后活跃时间 | 列表排序、空闲压缩判断、CAS |
| summarized_at | INTEGER | 上次摘要时间 | 判断 `summarized_at < last_active_at` |

索引：`(user_id, last_active_at DESC)`

**问题**：`mood` 是"整轮轨迹"，语义混乱；它需要的是"最后显示的表情"。

**v2 建议**：`mood` → `last_expression VARCHAR(16)`，只存 6 个表情词之一，不再存轨迹。

---

## 2. chat_memory（LLM 的聊天记录窗口）

| 字段 | 类型 | 作用 | 读/写 |
|---|---|---|---|
| id | INTEGER PK AUTOINCREMENT | 内部主键 | — |
| user_id | TEXT NOT NULL | 冗余，排查/跨锚点查询用 | — |
| anchor_id | TEXT NOT NULL | 属于哪个对话（隔离键） | 加载窗口 |
| role | TEXT NOT NULL | user / assistant / system / tool | LC4J 还原消息 |
| content | TEXT NOT NULL | LC4J 消息 JSON（含工具调用、thinking 等结构） | 读写 |
| mood | TEXT | **现状**：占位，注释写"预留：窗口消息按条渲染情绪用"，**实际无人读** | 死列 |
| created_at | INTEGER NOT NULL | 写入时间（每次整锚点重写，不是原始消息时间） | 排序 |

索引：`(anchor_id)`

**问题**：`mood` 是死列；`created_at` 不代表消息发生时间。

**v2 建议**：删 `mood`；`created_at` 保留但注释清楚。

---

## 3. cold_memory（全量对话归档 / 召回正文来源）

| 字段 | 类型 | 作用 | 读/写 |
|---|---|---|---|
| id | INTEGER PK AUTOINCREMENT | 内部主键，**同时作为 Qdrant 的 sourceId** | 召回后按 id 回查正文 |
| user_id | TEXT NOT NULL | 用户 | 索引 |
| role | TEXT NOT NULL | user / assistant | — |
| content | TEXT NOT NULL | 纯文本原文 | 深挖/召回 |
| mood | TEXT | **现状**：assistant 行的 AI 情绪轨迹；user 行恒 null | 目前无人读 |
| created_at | INTEGER NOT NULL | 发生时间 | 时间下界、衰减 |

索引：`(user_id, created_at)`

**问题**：存的是"AI 的表情轨迹"，而情绪召回真正需要的是"用户当时的情绪 + 强度"。

**v2 建议**：`mood` → `emotion VARCHAR(16)` + `intensity TINYINT`；user 行存用户情绪，assistant 行可空。

---

## 4. hot_memory（精炼档案卡）

| 字段 | 类型 | 作用 |
|---|---|---|
| user_id | TEXT NOT NULL | 用户 |
| type | TEXT NOT NULL | `user-impression` / `significant-event` / `commitment` |
| key | TEXT NOT NULL | 画像字段名（username/role/...）或事件/承诺的 UUID |
| value | TEXT NOT NULL | 字段值 / 事件描述 / 承诺文本（带方向前缀） |
| updated_at | INTEGER NOT NULL | 更新时间 |

主键：`(user_id, type, key)`

**问题**：append-only 类型（事件/承诺）没有"发生时间"，只能按 updated_at 近似（v0.3 债）。

**v2 建议**：结构不动；P1 再考虑给事件加发生时间。

---

## 5. soul_profile（AI 人格）

| 字段 | 类型 | 作用 |
|---|---|---|
| key | TEXT PK | role / name / tone / principles / forbidden_patterns / consistency_principles / quirks |
| value | TEXT NOT NULL | 对应文案 |
| updated_at | INTEGER NOT NULL | 更新时间 |

启动时 seed（已有不覆盖），运行期只读；每次请求注入 system prompt。

---

## 6. relation_state（新增·关系温度计）

| 字段 | 类型 | 作用 |
|---|---|---|
| user_id | TEXT PK | 用户 |
| familiarity | REAL 0~1 | 熟悉度：聊得越多、越深，缓慢增长；长期不聊不下降 |
| trust | REAL 0~1 | 信任：用户披露脆弱且回应得体时增长；越界时下降 |
| warmth | REAL -1~1 | 近期基调：正向/负向情绪的滑动平均，随时间衰减 |
| updated_at | INTEGER | 更新时间 |

用途：决定默认表情、语气松紧、是否适合主动关心（P2）；**不直接展示数值**。

---

## 7. 字段改名 / API 契约映射

| 层 | 现状 | v2 | 说明 |
|---|---|---|---|
| DB | `anchor_memory.mood` | `last_expression` | 存表情词，不存轨迹 |
| DB | `chat_memory.mood` | 删除 | 死列 |
| DB | `cold_memory.mood` | `emotion` + `intensity` | 用户情绪 |
| API | `GET /ai/anchors` 的 `mood` | `lastExpression` | 前端恢复表情用 |
| SSE | 事件 `mood {keyword}` | `presence {expression, intensity}` | 见情绪阶段 |
| 前端 | `currentMood` | `presence` | 情绪阶段再改 |

> DB 字段名、JSON 字段名、前端变量名是三件事，可以分开改；数据阶段先把 DB + JSON 对齐，情绪阶段再换 SSE 事件与前端状态。

---

## 8. 数据阶段执行方式（不改情绪行为）

原则：**先加后切、最后删**，保证任何一步中断都能跑。

1. **加新**：建 `relation_state`；`cold_memory` 加 `emotion/intensity`；`anchor_memory` 加 `last_expression`
2. **同步读写**：
   - `anchor.last_expression` 暂时写"现有 mood 轨迹的最后一个词"（行为不变）
   - `cold_memory.emotion/intensity` 暂时留空（等情绪阶段填）
   - `chat_memory` 停止写 `mood`（无人读）
   - API 增加 `lastExpression`，暂时保留 `mood` 别名
3. **验证**：启动、四轮对话、切锚、刷新、召回、摘要
4. **删除**（可放情绪阶段一起）：`chat_memory.mood`、`anchor.memory.mood`、`cold_memory.mood`

> 数据是否保留：SQLite 支持 `ALTER TABLE RENAME/DROP COLUMN`，可以原地迁移不丢现有对话；若选择重建库，会清空当前测试对话。**待用户确认**。
