package com.zoufx.ai.agent.config;

import com.zoufx.ai.agent.assistant.ChatAssistantContract;
import com.zoufx.ai.agent.tool.SessionSearchTool;
import com.zoufx.ai.agent.tool.TavilySearchTool;
import com.zoufx.ai.agent.tool.UserProfileTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配两个 ChatAssistantContract Bean：分别绑定 thinking / nonThinking 的 StreamingChatModel。
 * 共享同一个 ChatMemoryProvider——同 userId 跨模式切换历史连续。
 *
 * 挂载的工具集：
 *   - TavilySearchTool：网络检索
 *   - SessionSearchTool：经历流（Cold Archive）召回
 *   - UserProfileTool：把对方称呼晶化到 Hot Memory
 *
 * System prompt 由 SystemPromptComposer 在每次调用时动态生成
 * （角色 + 当前日期 + 身份分支 + 工具说明 + 全局规则）。
 */
@Configuration
public class AssistantConfig {

    @Bean
    public ChatAssistantContract thinkingAssistant(
            @Qualifier("thinkingChatModel") StreamingChatModel model,
            ChatMemoryProvider chatMemoryProvider,
            TavilySearchTool tavilySearchTool,
            SessionSearchTool sessionSearchTool,
            UserProfileTool userProfileTool,
            SystemPromptComposer composer) {
        return AiServices.builder(ChatAssistantContract.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessageProvider(composer.asProvider())
                .tools(tavilySearchTool, sessionSearchTool, userProfileTool)
                .build();
    }

    @Bean
    public ChatAssistantContract nonThinkingAssistant(
            @Qualifier("nonThinkingChatModel") StreamingChatModel model,
            ChatMemoryProvider chatMemoryProvider,
            TavilySearchTool tavilySearchTool,
            SessionSearchTool sessionSearchTool,
            UserProfileTool userProfileTool,
            SystemPromptComposer composer) {
        return AiServices.builder(ChatAssistantContract.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessageProvider(composer.asProvider())
                .tools(tavilySearchTool, sessionSearchTool, userProfileTool)
                .build();
    }
}
