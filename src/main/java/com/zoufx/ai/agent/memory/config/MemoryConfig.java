package com.zoufx.ai.agent.memory.config;

import com.zoufx.ai.agent.memory.property.MemoryProperties;
import com.zoufx.ai.agent.memory.api.ChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话记忆装配。
 * ChatMemoryStore 实现由 {@link com.zoufx.ai.agent.memory.impl.SqliteChatMemoryStore} 提供（@Component 自动注入），
 * 本地接口继承 LC4J 的 ChatMemoryStore，可直接作为 MessageWindowChatMemory.builder().chatMemoryStore() 入参。
 * 未来切 Redis / Postgres 只需替换该 Bean。
 */
@Configuration
@RequiredArgsConstructor
public class MemoryConfig {

    private final MemoryProperties props;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(props.getMaxMessages())
                .chatMemoryStore(chatMemoryStore)
                // 强制 system 消息位于 messages[0]：LC4J 默认 false 会把 system 追加到末尾，
                // 导致后续轮次 system 卡在历史中间，违反 OpenAI/Anthropic API 期望
                .alwaysKeepSystemMessageFirst(true)
                .build();
    }
}
