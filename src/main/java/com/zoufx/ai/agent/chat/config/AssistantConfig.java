package com.zoufx.ai.agent.chat.config;

import com.zoufx.ai.agent.chat.api.ChatAssistant;
import com.zoufx.ai.agent.prompt.support.PromptComposer;
import com.zoufx.ai.agent.tool.impl.ColdMemorySearchTool;
import com.zoufx.ai.agent.tool.impl.CommitmentRecordTool;
import com.zoufx.ai.agent.tool.impl.SignificantEventRecordTool;
import com.zoufx.ai.agent.tool.impl.TavilySearchTool;
import com.zoufx.ai.agent.tool.impl.UserImpressionUpdateTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配单一 {@link ChatAssistant} Bean，绑定当前激活 profile 的 streaming chatModel。
 *
 * <p>System prompt 由 {@link PromptComposer} 按 PromptSection 序列动态组装。
 * thinking 行为由模型自身决定（DeepSeek v4 always-on 自适应、MiniMax builder 期固定开启），
 * 不做 Java 层 per-call 覆盖（LC4J 1.13.1 langchain4j-anthropic 限制）。
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
            SignificantEventRecordTool significantEventRecordTool,
            CommitmentRecordTool commitmentRecordTool,
            PromptComposer composer) {
        return AiServices.builder(ChatAssistant.class)
                .streamingChatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessageProvider(composer.asProvider())
                .tools(tavilySearchTool, coldMemorySearchTool, userImpressionUpdateTool,
                        significantEventRecordTool, commitmentRecordTool)
                .build();
    }
}
