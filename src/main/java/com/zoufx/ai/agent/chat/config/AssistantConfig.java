package com.zoufx.ai.agent.chat.config;

import com.zoufx.ai.agent.chat.api.ChatAssistant;
import com.zoufx.ai.agent.chat.impl.SystemPromptComposer;
import com.zoufx.ai.agent.tool.impl.ColdMemorySearchTool;
import com.zoufx.ai.agent.tool.impl.TavilySearchTool;
import com.zoufx.ai.agent.tool.impl.UserImpressionUpdateTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配单一 {@link ChatAssistant} Bean，绑定当前激活 profile 装配的 {@code chatModel}。
 *
 * <p>v0.135 重构：旧版本装配 thinkingAssistant / nonThinkingAssistant 双 Bean，分别绑定双 ChatModel——
 * 该二分对 DeepSeek v4 hybrid（always-on 自适应）是伪二分；对 MiniMax 也无法做 per-call thinking
 * 覆盖（LC4J 1.13.1 langchain4j-anthropic 限制）。本类合并为单 Bean，thinking 行为由模型自身决定。
 *
 * <p>挂载的工具集：
 * <ul>
 *   <li>{@link TavilySearchTool}：网络检索</li>
 *   <li>{@link ColdMemorySearchTool}：记忆检索（冷内存搜索）</li>
 *   <li>{@link UserImpressionUpdateTool}：用户印象更新（hot_memory 的 user-impression type 写入）</li>
 * </ul>
 *
 * <p>System prompt 由 {@link SystemPromptComposer} 按 PromptSection 序列动态组装。
 */
@Configuration
public class AssistantConfig {

    @Bean
    public ChatAssistant chatAssistant(
            StreamingChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider,
            TavilySearchTool tavilySearchTool,
            ColdMemorySearchTool coldMemorySearchTool,
            UserImpressionUpdateTool userImpressionUpdateTool,
            SystemPromptComposer composer) {
        return AiServices.builder(ChatAssistant.class)
                .streamingChatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessageProvider(composer.asProvider())
                .tools(tavilySearchTool, coldMemorySearchTool, userImpressionUpdateTool)
                .build();
    }
}
