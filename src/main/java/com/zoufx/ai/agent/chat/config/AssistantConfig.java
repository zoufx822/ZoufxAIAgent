package com.zoufx.ai.agent.chat.config;

import com.zoufx.ai.agent.chat.api.ChatAssistant;
import com.zoufx.ai.agent.tool.impl.ColdMemorySearchTool;
import com.zoufx.ai.agent.tool.impl.TavilySearchTool;
import com.zoufx.ai.agent.tool.impl.HotMemoryUpdateTool;
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
 *   - HotMemoryUpdateTool：用户印象更新（热内存写入）
 *
 * System prompt 由 SystemPromptComposer 在每次调用时动态生成
 * （角色 + 当前日期 + 身份分支 + 工具说明 + 全局规则）。
 */
@Configuration
public class AssistantConfig {

    @Bean
    public ChatAssistant thinkingAssistant(
            @Qualifier("thinkingChatModel") StreamingChatModel model,
            ChatMemoryProvider chatMemoryProvider,
            TavilySearchTool tavilySearchTool,
            ColdMemorySearchTool coldMemorySearchTool,
            HotMemoryUpdateTool hotMemoryUpdateTool,
            SystemPromptComposer composer) {
        return AiServices.builder(ChatAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessageProvider(composer.asProvider())
                .tools(tavilySearchTool, coldMemorySearchTool, hotMemoryUpdateTool)
                .build();
    }

    @Bean
    public ChatAssistant nonThinkingAssistant(
            @Qualifier("nonThinkingChatModel") StreamingChatModel model,
            ChatMemoryProvider chatMemoryProvider,
            TavilySearchTool tavilySearchTool,
            ColdMemorySearchTool coldMemorySearchTool,
            HotMemoryUpdateTool hotMemoryUpdateTool,
            SystemPromptComposer composer) {
        return AiServices.builder(ChatAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessageProvider(composer.asProvider())
                .tools(tavilySearchTool, coldMemorySearchTool, hotMemoryUpdateTool)
                .build();
    }
}
