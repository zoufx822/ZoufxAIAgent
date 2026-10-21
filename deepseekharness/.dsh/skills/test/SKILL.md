---
name: test
description: ZoufxAIAgent 系统性测试（前后端 + SSE + 记忆 + UI）——按 git diff 推导测试矩阵，交子 Agent 跑真实服务，产出可复现的通过/失败报告，覆盖功能、边界、视觉、回归以及服务未启动等阻塞。
whenToUse: 当用户说「测一下」「跑测试」「/test」「回归验证」「验证这次改动」，或新功能完成后需要自测时使用。
disable-model-invocation: true
---

# /test — 系统性测试

## 调用方式

- `/test` — 测试最近变更（基于 git diff）
- `/test full` — 全量测试所有功能模块
- `/test <描述>` — 测试指定功能，如 `/test 流式输出` 或 `/test 会话记忆`

---

## 执行规范

**费上下文的步骤（跑测试、读长日志、浏览器操作）交给子 Agent（`subagent` 工具）执行，避免污染主上下文；审核、判定、「要不要修」由主 Agent 做。**

> 路径基准：下文 `src/main/java/...`、`pom.xml`、`./data/sqlite/...` 均相对**后端仓库 `E:\ZoufxDemo\ZoufxAIAgent`**；前端改动在独立仓库 `E:\ZoufxDemo\ZoufxAIAgent-Web`。

---

### 第一步：变更分析（主 Agent 执行）

```bash
git diff HEAD --name-only          # 变更文件列表
git diff HEAD --stat               # 变更概况
```

根据结果判断受影响的模块：

| 变更路径                             | 受影响模块                  |
|----------------------------------|------------------------|
| `src/main/java/**/controller/**` | API 接口、SSE 流           |
| `src/main/java/**/service/**`    | 业务逻辑、会话记忆              |
| `src/main/java/**/config/**`     | LangChain4J 配置、模型切换    |
| `src/main/resources/**`          | 配置项（API Key、模型、budget） |
| （前端仓库）`components/**` `lib/**` `hooks/**` | 前端：UI、SSE 解析、渲染 —— 在 `E:\ZoufxDemo\ZoufxAIAgent-Web` |

---

### 第二步：生成测试矩阵（主 Agent 执行）

在启动子 Agent 之前，**先推导出完整测试矩阵**，格式如下：

```
## 测试矩阵

### 功能测试（happy path）
- [ ] TC-01：...
- [ ] TC-02：...

### 边界 / 异常测试
- [ ] TC-10：...（空 prompt）
- [ ] TC-11：...（超长 prompt，500+ 字）
- [ ] TC-12：...（特殊字符：<script>、换行符、中英混合）

### 视觉 / 布局测试
- [ ] TC-20：...（元素是否溢出/截断）
- [ ] TC-21：...（加载状态样式是否正常）

### 回归测试
- [ ] TC-30：...（与改动相邻的功能）
- [ ] TC-31：...（本次未改动但共享状态的功能）
```

测试矩阵确定后再启动子 Agent。

---

### 第三步：子 Agent 执行测试

**子 Agent 的职责边界**：只执行与观测，不做取舍判定。测试虽是执行型任务，但**观测与判定容易出错**——历史上用弱模型跑，漏看 SSE 事件（工具明明触发却报"无 tool_call"）、误判通过/失败，整份报告不可信，主 Agent 得逐条回日志复核。对策：

- 矩阵条目写成"看到 X 就算通过"的可观测形式，别留需要临场解释的模糊项
- 报告里每条结论必须附**原始证据**（日志片段 / 响应体 / 截图路径），不接受"应该是通过了"
- 失败用例重测（第四步）同样交给子 Agent，且只测失败的 TC

用 `subagent` 工具启动（独立上下文，不占主会话），传入以下 prompt 模板：

```
你是测试工程师。请严格按照以下测试矩阵执行测试，不得跳过任何用例。

【测试矩阵】
{第二步生成的测试矩阵}

【测试约定】
- 服务地址：后端 http://localhost:8080；前端 http://localhost:3000
- 浏览器验证：若已配置 Playwright MCP 就用它操作 UI；**未配置时**改用 `curl` 打接口 + 读后端日志，
  并在报告里注明「未做浏览器验证」
- 每个测试用例结束后记录结果：✅ 通过 / ❌ 失败（附证据）/ ⚠️ 异常（描述现象）
- 发现失败时：描述复现步骤，不要自行修复，等待主 Agent 处理
- ==截图约定==：截图/日志一律写 `.dsh/tmp/`（工作区内），跑完由主 Agent 手动清理；需要留存的证据放 `.dsh/tmp/keep/`

【测试前准备】
1. 确认服务已启动（curl http://localhost:8080 或 mvn spring-boot:run；前端 curl http://localhost:3000）
2. 若用 Playwright MCP：开 browser_console_messages 监控并记录基线 error 数量

【执行顺序】
1. 功能测试（happy path 优先）
2. 边界异常测试
3. 视觉/布局检查
4. 回归测试
5. 输出完整测试报告

【测试报告格式】
## 测试报告
**时间**：{时间}
**触发范围**：{变更描述}

| 用例 | 描述 | 结果 | 备注 |
|-----|------|------|------|
| TC-01 | ... | ✅ | |
| TC-10 | ... | ❌ | 证据：.dsh/tmp/tc10-fail.png |

**Console Errors**：{数量}（基线 vs 测试后）
**失败用例**：{列表}
**待修复问题**：{列表}
```

---

### 第四步：处理失败（主 Agent 执行）

子 Agent 返回报告后：

1. **逐一分析失败用例**，确定根因
2. **修复代码**
3. **重新编译验证**：
   ```bash
   mvn compile          # 后端有改动时必须
   ```
4. **针对失败用例重新执行子 Agent 测试**（仅覆盖失败的 TC，不重跑全量）
5. 直到所有用例通过，输出最终报告

---

### 第五步：清理

DSH 没有「子 Agent 结束自动清理」的钩子（原 Claude Code 的 `SubagentStop` / `SessionStart` 钩子未迁移，原件归档在 `E:\ZoufxDemo\ZoufxAIAgent\claudecode\.claude\settings.json`）。约定：

- 截图、临时日志一律写 `.dsh/tmp/`，跑完由主 Agent 删除该目录
- 需要留存的失败证据放 `.dsh/tmp/keep/`，报告里引用相对路径
- 不要往两个仓库的工作区里写临时文件（会污染 `git status`）

---

## 异常识别清单

子 Agent 测试时，以下现象必须标记为 ❌ 或 ⚠️：

**功能异常**

- SSE 流中断（流式输出突然停止，无 `[DONE]`）
- 会话记忆丢失（第 2 轮对话不记得第 1 轮内容）
- thinking 模式未生效（发送了 `thinking: true` 但无 thinking 事件）
- 响应体为空或 JSON 解析错误
- HTTP 状态码非 200

**视觉异常**

- 打字机文字溢出容器
- Markdown 渲染后代码块无高亮
- thinking 区域展开/折叠状态错误
- 滚动条异常（内容未自动滚动到底部）
- 按钮/输入框不可点击或样式错位

**性能 / 稳定性异常**

- 页面加载后 `browser_console_messages(level=error)` > 0（仅在有 Playwright MCP 时适用）
- 同一 sessionId 发送第 10 轮消息时报错
- 切换 thinking 模式后上一次对话状态残留

**边界异常**

- 空 prompt 提交后无错误提示或服务崩溃
- prompt 含 `<script>alert(1)</script>` 时被执行（XSS）
- 超长输入（>2000 字）导致服务超时或截断

---

## 说明

- 子 Agent 用 `subagent` 工具启动，拥有独立上下文，不影响主会话
- 测试期间主 Agent 不修改代码，等待子 Agent 报告
- 测试截图用完即删（`.dsh/tmp/`），不留在仓库工作区
- 若服务未启动，先执行 `mvn spring-boot:run`（后端）与 `pnpm dev`（前端）再测试
