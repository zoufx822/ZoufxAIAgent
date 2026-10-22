# 版本演进与规划（截至 2026-10-21）

> 依据：`E:\obsidian-vaults\tech\Agent开发` 24 份文档的定位块 / 章节结构 / 版本历史，交叉核对当前代码与 git 历史。
> 图例：✅ 已落地 / 🟨 设计完成、未落地 / 📅 规划中

---

## 一、总览时间线

| 版本 | 日期 | 主题 | 状态 |
|---|---|---|---|
| v0.01 | 2026-04 | 持久化 + userId 取代 sessionId + 接口抽象 + 首次问称呼 | ✅ |
| v0.1 | 2026-04/05 | Memory Stream + Hot/Cold 分层 + Frozen Snapshot + 两个记忆工具 | ✅ |
| v0.11 | 2026-05 | 情绪双层（status + mood）+ 多字段 UserProfile + SOUL + 记忆 API | ✅ |
| v0.12 | 2026-05-19 | 函数式模块架构重构 + HikariCP（纯重构） | ✅ |
| v0.13 | 2026-05-21 | PromptSection SPI 编排器化 + hot_memory.type + yml 命名空间重整 | ✅ |
| v0.135 | 2026-05 下旬 | LLM Profile 化 + 单 ChatAssistant + capability 协商接口 | ✅ |
| v0.14 | 2026-05-23 | UserProfile 10 字段 + significant-event + commitment + mood 词表 10→6 | ✅ |
| v0.145 | 2026-05-26 | 记忆锚点（anchorId 隔离）+ 跨锚点摘要注入 + 前端视觉重构 | ✅ |
| v0.2 | 2026-06-08/09 | 语义 Recall 引擎（Qdrant + BGE-M3 + 三维加权 + MMR）+ mood sentinel + 画像引导自然化 | ✅ |
| v0.2.2 | 2026-06-24/29 | 动态思考深度 effort（三档）+ 上游取消 StreamingHandle | ✅ |
| v0.2.3 | 2026-07-02 | DEFERRED 落库（成功才落库）+ Service/Dao 分层 + 单事务 | ✅ |
| v0.2.4 | 2026-07-06 | consumeStream（生成脱离连接）+ 独立 stop 端点 + pending 轮询 + 失败回填 + 揭示引擎 + 摘要第一人称 | ✅ |
| v0.2.5 | 2026-07-18 | MySQL 9 + R2DBC 全反应式化 + 清库重来 | 🟨 方案完成，代码未实施 |
| 2026-10-21 收尾 | 2026-10-21 | 全库清理：mood 三处轨迹统一、幽灵 pending 双向探测、死代码/结构清理 | ✅（git `ce63c76`） |
| v0.3 | — | Consolidation Engine + 认知层债回收 | 📅 |
| v4 | — | Skills System（程序记忆三级加载） | 📅 |
| v5 | — | Artifact Generator + 注入扫描 | 📅 |
| v6 | — | Heartbeat 主动循环 | 📅 |

> 注意：`pom.xml` 版本是 `0.2.5-SNAPSHOT`，但代码实际停在 **v0.2.4 已落地 + v0.2.5 未实施**。版本号只是提前对齐，不代表功能完成度。

---

## 二、早期迭代明细（v0.01 → v0.145）

### v0.01：让"记得"先成立
- 记忆从 `InMemoryChatMemoryStore` 换成 SQLite，重启不丢
- 记忆分区键从 `sessionId` 改为 `userId`，前端 sidebar 的 session 概念降级为纯 UI
- 抽出记忆接口（`MemoryStore`）为未来换实现留口子
- 首次见面主动问称呼（身份识别）

### v0.1：从"记得"到"记得多、找得到"
- **Memory Stream**：所有经历按 userId 串成连续流（`cold_memory`），显式取消会话边界
- **Hot / Cold 分层**：Hot 常驻 prompt（画像/关键事实），Cold 按需检索
- **Frozen Snapshot**：system prompt 每次请求冻结一次，兼容 prompt cache
- 两个工具：`session_search`（检索经历流）、`remember_user_name`（写 Hot）
- 工程：业务接口反应式化、`LlmRetrySpec` 抽出

### v0.11："信息层补完"补丁版（不动 Recall）
- 双层情绪：`status` 6 态状态机（主） + `mood` 由 LLM 顺带输出（辅），二者正交
- UserProfile 扩为多字段
- **SOUL**：AI 自身人格/语气/原则/反模式（与 UserProfile 对偶）
- 记忆暴露 API + 前端 Heartbeat / StatePanel 接通

### v0.12：纯架构重构
- 按技术层组织 → 按业务域模块化（chat / memory / soul / tool …），模块内 api → config → impl/service
- 接入 HikariCP（池 5）
- 同期产出《记忆管理机制说明》（三层记忆、生命周期、Frozen Snapshot、Token 成本）与 72 条端到端测试用例（2026-05-20）

### v0.13：边界理顺
- `SystemPromptComposer` 单类大杂烩 → **`PromptSection` SPI 编排器**
- SOUL 文案外化、`UserImpressionFields` 收敛为单一来源（display_name → username）
- `hot_memory` 加 **type 维度**，user-impression 收敛为子类
- yml 命名空间重整（`spring.ai.*` → `ai.llm.*` 等），Properties 按业务模块归位

### v0.135：LLM 装配层收敛
- 合并 thinking / non-thinking 双 Bean → 单 `chatModel` + 单 `ChatAssistant`
- 以"行为 profile"（deepseek / minimax）而非协议组织装配
- 新增 `GET /ai/capabilities`（后改名 `/ai/features`）做能力协商，前端"思考"按钮按 capability 自适应
- 已知限制：LC4J Anthropic 模块无 `AnthropicChatRequestParameters`，per-call thinking 不可用 → minimax 声明不支持

### v0.14：让 AI"了解人"
- UserProfile 外表 5 + 内在 5 = 10 字段 + 完整度阈值（stranger / half-known / fully-known）+ 三段式主动了解
- 新增 `significant-event`（重要经历，append-only）
- 新增 `commitment`（双方承诺，append-only，带方向前缀）
- mood 词表从 10 精简到 6（平静/兴奋/难过/愤怒/好奇/困惑）

### v0.145：记忆锚点
- 对话隔离从 userId 下沉到 **anchorId**（`anchor_memory` 元数据表 + `chat_memory.anchor_id`）
- `@MemoryId` 直接传 anchorId，工具通过 anchor 表反查 userId（放弃 RequestContext/ThreadLocal）
- 跨锚点摘要注入 `AnchorContextSection`（order=28），三层衰减（近/中/远）
- 压缩时机：**文档设计为"切走锚点时异步压缩"+ 请求带 `prevAnchorId`**（后被实现演进推翻，见偏差清单）
- 前端大改：PresenceSticky（collapsed/spotlight）、Eyes 6 mood、Hot Memory 两区、Intimacy 三档、MemoryAnchors 三层、LookBackModal、Tweaks

---

## 三、当前主干（v0.2 → v0.2.4 已落地）

### v0.2：语义 Recall 引擎（2026-06-08/09）
- 向量库 **Qdrant**，embedding **BGE-M3**（OpenAI 兼容，1024 维），四类记忆全入索引
- 三维加权召回：`recency × importance × relevance` + MMR；召回结果经请求级 holder 注入 system prompt 末段 `RecallContextSection`（**不走 RetrievalAugmentor**，避免污染 chat_memory 窗口）
- SQLite 为唯一正本；Qdrant 只存向量 + 指针，不存正文；pointId 确定性派生（`userId|memType|sourceId`）
- FTS5 彻底下线；Hot 常驻注入收敛为"仅画像"，events/commitments 改召回浮现
- mood 标记改专用 sentinel `⟦mood:KEYWORD⟧`（替代 HTML 注释）
- 画像引导自然化（不硬追问、深度话题先回应）

### v0.2.2：动态思考深度（2026-06-24/29）
- `/ai/features` 声明 ThinkEffort；三档 normal / high / max，逐请求下发 `reasoning_effort`
- `Thinking{enabled, effort}` 值对象收口（未开思考则 effort 归一化为 null）
- Phase 7：用 `StreamingHandle` 在客户端停止/断开时掐断上游 LLM 流

### v0.2.3：成功才落库（2026-07-02）
- 从"边生成边落库"改为 **LLM 跑完才落库**，失败/停止/超时零残留（DEFERRED 语义）
- Service/Dao 分层：Dao 退纯 SQL（为换 R2DBC 铺路）
- `ChatMemoryService` 实现 LC4J `ChatMemoryStore` + write-back 内存缓冲
- 四表写入包进一个事务；Qdrant 索引在事务提交后 fire-and-forget
- 前端失败交互改为"消息回填输入框"

### v0.2.4：生成脱离连接（2026-07-06）
- **consumeStream**：生成挂后端自持订阅，客户端 SSE 只是 `Sinks.replay().all()` 视图
- turn 注册表 + 独立 `POST /ai/chat/stop`（返回 `{stopped}` 防"停止瞬间刚好完成"）
- `GET /ai/anchors/{id}/pending` 暴露在建轮，前端超时/断连改轮询
- 统一判定：**断连 ≠ 失败**，只有 error 事件才立即回填
- 前端揭示引擎（块级淡入）、锚点摘要改小Z 第一人称

### 2026-10-21 全库清理（git `ce63c76`）
- mood 三处轨迹统一、幽灵 pending 双向探测、死代码/结构清理
- 这次清理把 pom 推到过 0.2.6，后又回退 0.2.5（`a6bf181`）

---

## 四、v0.2.5：已设计、未实施（🟨）

- MySQL 9（Docker，utf8mb4）+ `r2dbc-mysql` + `DatabaseClient` 手写 SQL
- 五表 DDL 集中 `schema.sql`（`spring.sql.init`），删全部存量迁移代码
- DAO 全面反应式、`xxxAsync` 后缀退役；编排层（prepare / persistTurn / Scheduler / Backfill）同步反应式化
- `persistTurn` 换 `TransactionalOperator`；事务链禁混 `Blocking`
- 不迁数据：SQLite + Qdrant 全清空重来
- 关键约束：`ChatMemoryStore` 同步契约靠 write-back 内存缓冲；`key` 保留字反引号；`returnGeneratedValues("id")`

---

## 五、后期规划（📅）

### v0.3：Consolidation Engine + 认知层债
**主线**：先 Periodic Nudges 起步，后补 Cognitive Load + Markdown 可读视图；把"记忆写入"从 in-conversation 工具改成 Observe → Reflect → Crystallize 的后台抽取。

**必须回收的债**（总设计方案 §十 明确列出）：
1. **承诺履行追踪**：commitment 加状态（pending/fulfilled/broken）+ `update_commitment_status` 工具，双方承诺各自追踪
2. **Impression 评估 LLM 版**：替换现行"非空字段计数"（会把"不知道"算 1 分）
3. **多 type Consolidation**：significant-event / commitment 去重、合并、长期沉淀
4. **有序 hot 端点**：`GET /hot/recent?type=&limit=`（现行 Map 无序无时间）
5. **工具节制**：三个记忆写入工具 + 大段 promptInstructions 移出工具列表，改后台异步抽取
6. **超窗滚动压缩**：当前是 20 条 FIFO 直接丢弃，改为压缩而非丢
7. **渐进披露**：工具 prompt 分级加载（与 v4 Skills 的 L1/L2/L3 同思路）
8. **承诺感知补偿**：未兑现承诺即使不被语义召回也要浮现

### v4：Skills System
- 程序记忆具象化为可命名、可演化的 skill 文档（Markdown）
- 三级渐进加载：L1 名称+一句话（常驻）/ L2 参数（决定调用时）/ L3 完整步骤（执行时）

### v5：Artifact Generator
- 巩固的副产物：笔记 / 纪要 / 待办清单，可命名、可引用、可再加工
- 写入长期记忆前的 **prompt injection 扫描**（Tavily 搜索结果入记忆的注入风险）

### v6：Heartbeat 主动循环
- Spring `@Scheduled` 心跳（默认 30 分钟）读待办清单，由 LLM 决策是否主动行动
- 补足"AI 在没人说话时完全静默"的空白

### 跨版本的架构约定（总设计方案）
- 记忆子系统应抽象为 `MemoryStore` / `RecallEngine` / `ConsolidationEngine` 三层接口
- 对外提供人类可读的 Markdown 记忆视图（调试 / 用户控制 / 迁移 / 信任）
- 明确不取：多渠道部署、技能市场、纯 Markdown 作为唯一载体、多 Agent Workspace

---

## 六、文档与代码偏差（读文档时必须知道）

| # | 文档说法 | 代码实况 |
|---|---|---|
| 1 | v0.145：切走锚点时压缩 + `prevAnchorId` | 实际是 `ChatScheduler` 每 10min 扫空闲锚点，前端已不传 `prevAnchorId` |
| 2 | v0.145：`POST /ai/memory/anchor`、`/ai/memory/anchor/{id}/messages` | 实际 `/ai/anchors`、`/ai/anchors/{id}/messages` |
| 3 | v0.135：`GET /ai/capabilities` | 实际 `/ai/features` |
| 4 | v0.14：mood 词表 6 个 | 代码 `Moods.ALL` 是 7 个（多「愉快」） |
| 5 | v0.145：PresenceSticky collapsed 56px / spotlight 170px、3s 回收 | 未实现，常驻 118px sticky |
| 6 | v0.2.5：MySQL + R2DBC | 未实施，仍是 SQLite + JdbcTemplate |
| 7 | v0.2 文档提到"并行瞬时情绪分类兜底" | 已无此类，纯 LLM 标记驱动 |
| 8 | v0.11：mood 标记放末尾、每轮 0~1 次 | 现为"开头必打 + 转折可追加（可多个）" |

> 读文档的顺序原则：**版本历史 > 正文**。版本历史记录了同版内的多次修订与推翻，正文只反映最后一次修订的静态描述；跨版本冲突以最新版本历史为准。

---

## 七、对当前待办的影响

- 待办 1（MySQL/R2DBC）= 直接执行 v0.2.5 方案，不重新设计
- 待办 2（情绪闪变）= v0.2 起"一轮可多次情绪"是设计使然，但**缺少最小展示时长**；修法要补前端节拍，不是回退设计
- 待办 3（啰嗦）= v0.11/v0.13 都只有"信息密度高于装饰"软约束，没有字数上限；v0.3 的"渐进披露/工具节制"会顺带减负
- 表情位置 = v0.145 的 spotlight 机制未实现，属于**补设计**而非改设计
- v0.3 的工具节制/滚动压缩与"啰嗦"同源，可提前做其中一部分
