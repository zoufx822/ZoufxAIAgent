# DATA-MODEL.md · 数据模型

> SQLite `./data/sqlite/zoufx-ai.db`；MySQL/R2DBC 暂缓。
> 变更策略：**旧库删库重建，不写迁移**；同时删 `./data/qdrant`。

## 六张表

```
用户
 ├─ soul_profile       AI 人格（全局）
 ├─ relation_state     关系温度（每用户一行）
 ├─ hot_memory         画像 / 重要经历 / 承诺
 ├─ anchor_memory      对话目录（每个对话一行）
 │    └─ chat_memory   窗口消息（≤20 条，给 LLM）
 └─ cold_memory        全量原文（跨对话，召回用）
```

anchor 是"对话封面"，chat_memory 才是消息，cold_memory 是原文备份。

## 1. anchor_memory

| 字段 | 类型 | 作用 |
|---|---|---|
| id | TEXT PK | 锚点 UUID |
| user_id | TEXT NOT NULL | 归属用户 |
| title | TEXT | 标题（可 PATCH） |
| summary | TEXT | 滚动摘要（空闲压缩） |
| last_expression | TEXT | 最后显示的表情词，刷新恢复用 |
| created_at | INTEGER | 创建时间 |
| last_active_at | INTEGER | 最后活跃；排序、空闲压缩、CAS |
| summarized_at | INTEGER | 摘要水位；`< last_active_at` 表示有新内容 |

索引：`(user_id, last_active_at DESC)`

## 2. chat_memory

| 字段 | 类型 | 作用 |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | 内部主键 |
| user_id | TEXT NOT NULL | 冗余 |
| anchor_id | TEXT NOT NULL | 隔离键 |
| role | TEXT NOT NULL | user / assistant / system / tool |
| content | TEXT NOT NULL | LC4J 消息 JSON |
| created_at | INTEGER | 写入时间（整锚点重写） |

索引：`(anchor_id)`；无 `mood` 列。
写入：`ChatMemoryService` write-back；成功轮 `DELETE + INSERT` 全量替换，失败/停止不写。

## 3. cold_memory

| 字段 | 类型 | 作用 |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | 主键，同时是 Qdrant sourceId |
| user_id | TEXT NOT NULL | 用户 |
| role | TEXT NOT NULL | user / assistant |
| content | TEXT NOT NULL | 纯文本原文 |
| emotion | TEXT | 用户情绪（user 行有值） |
| intensity | INTEGER | 1–5 |
| created_at | INTEGER | 发生时间 |

索引：`(user_id, created_at)`
Qdrant pointId 由 `userId|memType|sourceId` 确定性生成；清库必须两边同时清。

## 4. hot_memory

| 字段 | 类型 | 作用 |
|---|---|---|
| user_id | TEXT NOT NULL | 用户 |
| type | TEXT NOT NULL | user-impression / significant-event / commitment |
| key | TEXT NOT NULL | 画像字段名或事件/承诺 UUID |
| value | TEXT NOT NULL | 值/描述/承诺文本 |
| updated_at | INTEGER | 更新时间 |

PK `(user_id, type, key)`；画像 UPSERT，事件/承诺 append-only。

## 5. soul_profile

| 字段 | 类型 | 作用 |
|---|---|---|
| key | TEXT PK | role / name / tone / principles / forbidden_patterns / consistency_principles / quirks |
| value | TEXT NOT NULL | 文案 |
| updated_at | INTEGER | 更新时间 |

启动 seed（已有不覆盖），运行期只读。

## 6. relation_state

| 字段 | 类型 | 作用 |
|---|---|---|
| user_id | TEXT PK | 用户 |
| familiarity | REAL 0–1 | 熟悉度 |
| trust | REAL 0–1 | 信任 |
| warmth | REAL -1–1 | 近期基调 |
| updated_at | INTEGER | 更新时间 |

`persistTurn` 事务内更新；目前只写不读。

## 命名

| 名字 | 含义 |
|---|---|
| `emotion` | **用户**情绪（分类器输出） |
| `intensity` | 用户情绪强度 1–5 |
| `last_expression` | **页面显示**的表情 |
| `presence` | SSE 事件，不落库 |
| `affect` | 分类结果，仅内存 `Turn` |

旧 `mood`（AI 自报情绪）已删除：`anchor_memory.mood`、`chat_memory.mood`、`cold_memory.mood` 均不存在。

## 写入/读取

| 表 | 写 | 读 |
|---|---|---|
| anchor_memory | persistTurn（touch/title）、AnchorService（summary） | 列表、context、压缩扫描 |
| chat_memory | ChatMemoryService.commit | LC4J 窗口、GET messages |
| cold_memory | persistTurn（user+assistant） | 召回 hydrate、深挖 |
| hot_memory | HotMemoryService.commit | prompt 注入、右栏 |
| soul_profile | 启动 seed | 每轮 system prompt |
| relation_state | RelationStateService.apply | 暂无 |
