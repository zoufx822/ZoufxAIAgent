package com.zoufx.ai.agent.chat.api;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4J AiService 接口。
 * 由 AiServices.builder(...) 动态代理实现，自动接管会话记忆、流式、工具调用等。
 * {@code @MemoryId} 绑定到 anchorId（记忆按锚点窗口隔离），
 * 需要 userId 的下游通过 {@code AnchorMemoryStore.findUserId(anchorId)} 反查。
 * 系统提示由 AssistantConfig#systemMessageProvider 在运行时动态生成（注入当前日期 + 身份识别）。
 */
public interface ChatAssistant {
    TokenStream chat(@MemoryId String anchorId, @UserMessage String userMessage);
}
