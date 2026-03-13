# CLAUDE.md

本文档为 Claude Code (claude.ai/code) 在本项目中工作时提供指导。

## 项目概述

这是一个集成了 LangChain4J 的 Spring Boot 4.0.3 应用，通过 Anthropic 兼容接口连接 MiniMax AI API。支持带会话记忆的流式聊天功能。

## 命令

```bash
# 构建项目
mvn clean compile

# 运行应用
mvn spring-boot:run

# 测试聊天接口
curl -X POST http://localhost:8080/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "你好", "sessionId": "test"}'
```

## 架构

项目采用标准 Spring Boot 分层架构：

```
src/main/java/com/zoufxdemo/zoufxlangchain4j/
├── ZoufxLangChain4JApplication.java    # 应用入口
├── config/
│   └── LangChain4JConfig.java          # LangChain4J + MiniMax API 配置
├── controller/
│   └── AIChatController.java           # REST API（支持 SSE 流式响应）
├── model/
│   └── ChatRequest.java                # 请求 DTO
└── service/
    ├── ChatMemoryService.java          # 会话记忆服务接口
    └── impl/
        └── ChatMemoryServiceImpl.java  # 内存实现
```

## 关键技术点

- **流式响应**：使用 Spring WebFlux 的 `Flux<ServerSentEvent>` 实现 Server-Sent Events
- **会话记忆**：使用内存 `ConcurrentHashMap` 存储会话；实现 `ChatMemoryService` 接口可切换到 Redis
- **AI 集成**：LangChain4J Anthropic 模块连接 MiniMax API（`https://api.minimaxi.com/anthropic/v1`）
- **思考过程**：配置 `thinkingType: enabled`，token 预算 2048

## API 接口

- **POST /ai/chat** - 带会话记忆的流式聊天
  - 请求体：`{"prompt": "...", "sessionId": "..."}`
  - 响应：SSE 流，包含 `thinking` 和 `content` 事件

## 开发规范

- 使用 `@Slf4j` (Lombok) 记录日志
- 使用 Spring 的 `@Autowired` 或构造函数注入
- 使用 Spring 的 `StringUtils.hasText()` 进行 null/空字符串检查
- 为可能需要替代实现的 Service 保持接口（如内存→Redis）

## 测试规范

- 前端新增功能时，使用 puppeteer 启动 Chrome 浏览器打开页面，实际操作页面多次测试新增功能，观察页面行为是否符合预期且稳定，将测试结果告诉我
- 前端有变动时，使用 puppeteer 启动 Chrome 浏览器打开页面，实际操作页面多次测试改动相关的功能点，观察页面行为是否符合预期且稳定，将测试结果告诉我
- 使用 puppeteer 测试完成后，清理掉自动化测试脚本文件、截图文件等测试时创建的文件

## 前端现代化全屏侧边栏布局优化项目进展（2026-03-12）

### 项目概述
当前正在实现现代化全屏侧边栏布局UI优化，将原有极简对话框扩展为全屏现代化布局，增加左侧固定宽度侧边栏（UI占位符），采用明亮现代简约风格（类似Notion/Figma界面）。

### 当前状态
- **工作分支**: `feature/modern-ui-sidebar-layout` (在`.worktrees/modern-ui-sidebar-layout`目录中)
- **进度**: 已完成阶段1-4，阶段5（测试和优化）待完成
- **应用启动**: `mvn spring-boot:run` 后访问 `http://localhost:8080`

### 已完成的阶段

#### 阶段1：基础布局重构（已完成）
1. **CSS变量系统扩展**: 添加明亮现代主题变量（纯白背景、深灰文本、紫色主色调等）
2. **全屏Flex布局实现**: `.app-layout`、`.sidebar`、`.main-content`布局容器
3. **HTML结构调整**: 添加AppLayout容器和侧边栏HTML结构

#### 阶段2：组件重构（已完成）
1. **新组件创建**: `AppLayout.js`（整体布局）、`Sidebar.js`（侧边栏UI占位符）
2. **ChatWindow重构**: 移除外层宽度限制，适应全屏布局
3. **Vue应用入口更新**: `main.js`注册`AppLayout`组件而非`ChatWindow`

#### 阶段3：视觉设计实现（已完成）
1. **明亮主题应用**: 所有UI元素使用新的CSS变量
2. **组件样式优化**: 消息气泡、输入框、按钮等适应明亮背景
3. **代码高亮适配**: 将Prism.js主题从`prism-tomorrow`改为`prism.min.css`适应明亮主题

#### 阶段4：响应式适配（已完成）
1. **平板端适配** (768px-1023px): 侧边栏可折叠（悬停展开）
2. **移动端适配** (<768px): 侧边栏隐藏，全屏聊天，显示标题栏

### 剩余任务（阶段5：测试和优化）

#### 测试项目
1. **功能测试**: 验证所有现有聊天功能（发送、流式SSE、Markdown渲染、代码高亮等）
2. **响应式测试**: 桌面端（≥1024px）、平板端（768px-1023px）、移动端（<768px）
3. **视觉验证**: 明亮现代简约风格美学，代码高亮可读性，色彩对比度
4. **质量验证**: 无JavaScript错误，无障碍支持（ARIA属性），性能无显著下降

#### 优化任务
1. **CSS清理**: 移除不再需要的旧样式规则
2. **代码质量**: 检查并优化组件代码
3. **最终验证**: 运行Puppeteer测试确保稳定性

### 明天继续工作的步骤
1. **进入工作树**: `cd .worktrees/modern-ui-sidebar-layout`
2. **启动应用**: `mvn spring-boot:run`
3. **开始测试**: 运行阶段5的测试和优化任务
4. **完成工作**: 使用`superpowers:finishing-a-development-branch`技能完成开发分支

### 关键文件修改
1. `src/main/resources/static/css/main.css` - 扩展CSS变量，添加布局和响应式样式
2. `src/main/resources/static/index.html` - 更新HTML结构，添加侧边栏
3. `src/main/resources/static/js/main.js` - 更新Vue组件注册
4. `src/main/resources/static/js/components/AppLayout.js` - 新整体布局组件
5. `src/main/resources/static/js/components/Sidebar.js` - 新侧边栏组件
6. `src/main/resources/static/js/components/ChatWindow.js` - 移除宽度限制，移除标题栏

### 设计要点
- **美学风格**: 明亮现代简约，类似Notion/Figma，避免通用AI美学（紫色渐变等）
- **色彩系统**: 白色/浅灰背景，深灰文本，紫色主色调，功能蓝色/红色
- **布局**: 全屏Flex布局，左侧固定280px侧边栏，主内容区域占据剩余空间
- **响应式**: 平板端侧边栏折叠，移动端侧边栏隐藏

## 阶段5：测试与优化完成（2026-03-13）

### 测试结果
- **Puppeteer自动化测试**: 10/10 测试通过 (100% 成功率)
- **功能完整性**: 所有聊天功能正常工作
- **响应式适配**: 桌面端、平板端、移动端布局正确
- **视觉美学**: 明亮现代简约风格实现，代码高亮可读
- **质量保证**: 无JavaScript错误，无障碍支持完善

### 修复的关键问题
1. **Header渲染问题**: 添加隐藏的span元素确保Vue渲染header元素
2. **移动端布局**: 验证CSS媒体查询正确应用，header在移动端显示为block
3. **测试脚本优化**: 修复waitForTimeout问题，增强错误处理

### 验证的成功标准
- ✅ 全屏布局，无固定宽度限制
- ✅ 左侧固定宽度侧边栏（280px）显示正常
- ✅ 所有现有聊天功能正常工作
- ✅ 代码高亮在明亮主题下可读
- ✅ 会话记忆功能正常
- ✅ 明亮现代简约风格美学实现
- ✅ 简约功能性视觉风格
- ✅ 独特的非通用AI界面美学
- ✅ 色彩系统协调，对比度适当
- ✅ 字体和间距系统优化
- ✅ 桌面端、平板端、移动端布局正确
- ✅ 无JavaScript错误
- ✅ 无障碍支持（ARIA属性完整）
- ✅ 代码质量保持或提升
- ✅ 性能无显著下降

### 测试文件清理
- 已清理测试脚本和截图文件
- 保持代码库整洁

### 下一步
使用`superpowers:finishing-a-development-branch`技能完成开发分支工作。