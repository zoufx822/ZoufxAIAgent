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