# /test-prompt — System Prompt 编排的 Prompt-Behavior 系统测试

验证 `SystemPromptComposer` + 所有 `PromptSection` 实现注入的每条 directive 是否==真的驱动了 LLM 行为==。专治"prompt 文本看着对、LLM 行为却不对"的盲区——比如 v0.13 SOUL principles 那条"陌生人问称呼"软语气被 LLM 默默忽略，结构验证全过，跑了多轮才发现。

==与 `/test` 的区别==：`/test` 测功能/接口/UI，关注"代码是否跑通"；`/test-prompt` 测提示词，关注"==LLM 行为是否符合 prompt 预期=="。两者互补，不重叠。

## 调用方式

- `/test-prompt` — 测试==当前所有== PromptSection 的全部 directive
- `/test-prompt <Section名>` — 只测指定 Section，如 `/test-prompt Identity` / `/test-prompt Soul`
- `/test-prompt diff` — 只测最近 git diff 涉及到的 PromptSection（增量验证）

---

## 核心原则（区别于 /test）

1. **必须跑真实 LLM** —— 静态检查（看 prompt 文本是否对、字段是否替换）==不算通过==。LLM 处理 prompt 不可静态推断，软语气、错误条件、跨 Section 冲突==只有真实多轮对话才能暴露==
2. **必须正反两组用例** —— 每条 directive 配：(a) 条件满足时 LLM 是否==按期望行动==？(b) 条件不满足时 LLM 是否==不出现该行为 / 走对偶分支==？正向偏置是 prompt 工程最常翻车的盲区
3. **测试前两次审核** —— 步骤 1 拆 directive 完输出给用户审一遍；步骤 2 矩阵设计完再输出审一遍。==避免漏拆==关键 directive（如分支型 Section 的隐藏分支）
4. **状态切换必须覆盖** —— 不只测"hot 有值/无值"两个静态状态，==还要测状态切换瞬间==（如 stranger → known 那一轮、language 自动检测后下一轮）

---

## 执行规范

**所有费上下文的步骤（枚举源码、跑测试）放子 Agent；审核与决策回到主 Agent。**

---

### 第一步：枚举 directive（主 Agent 执行）

```bash
# 找所有 PromptSection 实现
```
用 Grep `implements PromptSection` 找全实现类。对每个实现 Read 其 `render()` 方法，==逐条==拆出 directive。

输出格式如下表，==输出后暂停==，让用户审核是否有漏拆（特别注意 `if/else` 分支、`switch` 各 case、动态拼接的子段）：

```
## Directive 清单

| # | Section | 触发条件 | Directive 原文（缩略） | 期望行为 |
|---|---------|----------|----------------------|----------|
| 1 | Identity (username 已知分支) | hot.username 非空 | "对方的称呼是「X」...直接称呼...每轮至少用一次..." | AI 每轮回复至少自然带一次称呼 |
| 2 | Identity (username 缺失分支) | hot.username 为空 | "你还不知道对方的称呼...本轮自然引一次问称呼..." | 首轮（或新一轮）AI 在回复中至少问一次称呼 |
| 3 | Identity (language) | hot.language 非空 | "对方使用的语言是 {}..." | AI 按该语言回复 |
| 4 | Soul (principles 第 1 条) | enabled-keys 含 principles | "信息密度高于装饰，回答尽量精炼" | AI 不堆砌寒暄、不绕弯 |
| ... | | | | |
```

==关键审核点==：
- 分支型 Section 是否每个分支都拆了（如 IdentityPromptSection 的 username 已知/缺失两路）
- 多 bullet 的 directive（如 SOUL principles 多条）是否==逐条拆开==
- 跨字段交互（如 username 已知时 username directive 与"如何问称呼"明显冲突，需要==专门设计冲突用例==）

主 Agent 在此处停下来：
> "已枚举 N 条 directive，请审核是否漏拆 / 是否有需要合并的条目，确认后回复 OK 进入步骤 2"

---

### 第二步：设计正反测试矩阵（主 Agent 执行）

对每条 directive 设计正反用例，输出后==再次暂停==请用户审核：

```
## 测试矩阵

| 用例 | 验证 directive # | 类型 | 前置状态 | 操作 | 期望 |
|------|----------------|------|----------|------|------|
| TP-01 | #1 (username 已知) | 正向 | hot.username=ZFX | 发"今天天气怎么样" | 回复至少自然带一次"ZFX" |
| TP-02 | #1 | 反向 | hot.username 缺失 | 同上 | 回复==不含==任何名字（避免幻觉造名） |
| TP-03 | #2 (username 缺失) | 正向 | hot.username 缺失，全新 userId | 发"你好" | 回复==至少==自然引一次问称呼 |
| TP-04 | #2 | 反向 | hot.username=ZFX | 发"你好" | 回复==不应==再问称呼 |
| TP-05 | #2 (重复抑制) | 边界 | hot.username 缺失，chat 已有 AI 问过称呼 + 用户拒答 | 发"今天天气" | 回复==不应==再追问称呼 |
| TP-06 | #1 + #2 冲突 | 状态切换 | 第 1 轮 username 缺失发"我叫 ZFX"，第 2 轮发"再问下天气" | 第 2 轮回复带"ZFX"，不再问称呼 |
| ... | | | | | |
```

==状态控制工具==：
- **全新 userId**：浏览器 `browser_evaluate('() => localStorage.clear()')` + `browser_navigate` 刷新
- **指定 hot 字段**：调 Memory Controller API（如有）/ 手工 `sqlite3 ./data/zoufx-ai.db "INSERT INTO hot_memory ..."`
- **清空 anchor memory**：调 Memory Controller delete / SQL `DELETE FROM anchor_memory WHERE user_id=...`

每条 ❌/⚠️ 必须能复现，==前置状态要写到能让人精确复现的程度==。

主 Agent 在此处停下来：
> "已设计 N 个用例，请审核是否覆盖所有 directive 的正反、是否有遗漏的状态切换场景，确认后回复 OK 启动子 Agent"

---

### 第三步：子 Agent 跑测试（Task tool，haiku 模型）

==与 `/test` 同款约定==：haiku 模型，Playwright MCP 操作浏览器，截图只传纯文件名落 `.playwright-mcp/`。

传入子 Agent 的 prompt 模板：

```
你是 prompt-behavior 测试工程师。按以下矩阵执行真实 LLM 多轮对话测试。

## 服务地址（本项目特殊：前端独立仓库）
- 前端 UI：http://localhost:3000  ← Playwright 操作
- 后端 API：http://localhost:8080
- 测试前确认两个服务都 200。若任一未启动，直接报错退出。

## 后端日志位置
{主 Agent 填入实际后台任务的 output 路径}

LC4J DEBUG 日志会打印每次请求的完整 SystemMessage / UserMessage / AiMessage / Tool calls，
是判定"是否生效"的==关键证据==——每个用例的报告必须引用对应时间窗的 system message 片段。

## 测试矩阵
{主 Agent 填入步骤 2 审核通过的矩阵}

## 状态控制
{主 Agent 填入步骤 2 表中的状态控制工具列表}

## 判定规则
- ✅ 生效：行为完全符合 directive 期望
- ⚠️ 部分生效：条件满足但行为弱（如该问称呼时只用"您"未明确问；该用名字时只在第一轮带、后续轮丢失）
- ❌ 失效：条件满足但行为==完全没出现==（如 v0.13 不问称呼那种）
- 🟡 冗余/冲突：多条 directive 同时生效但行为打架（如 SOUL 和 Identity 重叠后 LLM 在两种语气间摇摆）

## 报告格式

| 用例 | 验证 # | 结果 | LLM 实际输出片段 | system message 片段 | 根因猜测 |
|------|--------|------|----------------|-------------------|---------|

==不要==自己改代码或 prompt。每条 ❌/⚠️/🟡 给根因猜测但等主 Agent 决策。
```

子 Agent 必须做的事：
1. 先 curl 确认前后端 200
2. ==按用例顺序==跑，每个用例独立结束后再开下一个（避免上下文污染）
3. 状态切换用例要==精确观察==切换前后的 system message 对比
4. 报告时 ❌/⚠️ 必须附 system message 实证（用 Read 工具读后端日志）

---

### 第四步：分类报告 + 决策（主 Agent 执行）

子 Agent 返回后，主 Agent 分类整理：

```
## /test-prompt 报告

**测试范围**：{全部 / 指定 Section / diff}
**Directive 数**：{N}
**用例数**：{M}

### ✅ 生效（{n} 条）
{逐条列出 + 一句话证据}

### ⚠️ 部分生效（{n} 条）
{逐条列出 + 现象 + 根因猜测}

### ❌ 失效（{n} 条）
{逐条列出 + 现象 + 根因猜测 + 修复建议方向}

### 🟡 冗余/冲突（{n} 条）
{逐条列出 + 哪两条 directive 在哪个场景冲突 + 合并/裁剪建议}

### 优化空间
{超出"通过/失败"的发现：可合并的 directive、可删的冗余、可强化的软语气、可显式化的隐式条件}
```

==报告完成后==主 Agent 停下来：
> "测试结果如上。⚠️/❌/🟡 共 N 条需要决策，要修哪些 / 怎么修等你拍。"

==绝不==自己改代码或 yml——这一步用户必须显式发命令。

---

## 服务前置要求

- 后端 8080 + 前端 3000 都必须在跑
- 若未跑，主 Agent 启动前先 ==询问用户==（不擅自重启正在跑的服务）
- ==关键==：后端必须是当前代码——若刚改过 PromptSection / yml，先确认重启过（项目无 devtools）

---

## 注意事项

- 跑期间主 Agent 不修改代码，等子 Agent 报告
- 测试用例的"前置状态"必须==可精确复现==（userId、hot KV、chat memory 都写明）
- 截图沿用 `/test` 约定：纯文件名落 `.playwright-mcp/`，会话级钩子自动清理
- 报告里 ⚠️/❌/🟡 的"根因猜测"==只是猜测==，是给主 Agent 决策时的参考，不代表确诊
- 跑完不主动关闭服务

---

## 何时不该用 /test-prompt

- 改的是==非提示词代码==（如工具实现、记忆存储、controller）——用 `/test`
- 改的是==前端==（UI 渲染、SSE 解析）——用 `/test`
- 仅改 prompt 文本拼写错误且确信不影响行为——肉眼审 yml 即可
