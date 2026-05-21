package com.zoufx.ai.agent.chat.config;

import com.zoufx.ai.agent.chat.api.ChatAssistant;
import com.zoufx.ai.agent.tool.impl.ColdMemorySearchTool;
import com.zoufx.ai.agent.tool.impl.TavilySearchTool;
import com.zoufx.ai.agent.tool.impl.UserImpressionUpdateTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配两个 ChatAssistant Bean：分别绑定 thinking / nonThinking 的 StreamingChatModel。
 * 共享同一个 ChatMemoryProvider——同 userId 跨模式切换历史连续。
 *
 * 挂载的工具集：
 *   - TavilySearchTool：网络检索
 *   - ColdMemorySearchTool：记忆检索（冷内存搜索）
 *   - UserImpressionUpdateTool：用户印象更新（hot_memory 的 user-impression type 写入；v0.13 起从 HotMemoryUpdateTool 重命名）
 *
 * System prompt 由 SystemPromptComposer 按 PromptSection 序列动态组装
 * （v0.13：顶部 role+date + SoulPromptSection + IdentityPromptSection + ToolsPromptSection + MoodPromptSection）。
 */
@Configuration
public class AssistantConfig {

    @Bean
    public ChatAssistant thinkingAssistant(
            @Qualifier("thinkingChatModel") StreamingChatModel model,
            ChatMemoryProvider chatMemoryProvider,
            TavilySearchTool tavilySearchTool,
            ColdMemorySearchTool coldMemorySearchTool,
            UserImpressionUpdateTool userImpressionUpdateTool,
            SystemPromptComposer composer) {
        return AiServices.builder(ChatAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessageProvider(composer.asProvider())
                .tools(tavilySearchTool, coldMemorySearchTool, userImpressionUpdateTool)
                .build();
    }

    @Bean
    public ChatAssistant nonThinkingAssistant(
            @Qualifier("nonThinkingChatModel") StreamingChatModel model,
            ChatMemoryProvider chatMemoryProvider,
            TavilySearchTool tavilySearchTool,
            ColdMemorySearchTool coldMemorySearchTool,
            UserImpressionUpdateTool userImpressionUpdateTool,
            SystemPromptComposer composer) {
        return AiServices.builder(ChatAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessageProvider(composer.asProvider())
                .tools(tavilySearchTool, coldMemorySearchTool, userImpressionUpdateTool)
                .build();
    }
}
