package com.zoufx.ai.agent.config.ai;

import com.zoufx.ai.agent.config.properties.ChatMemoryProperties;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话记忆装配。
 * 当前使用官方 InMemoryChatMemoryStore；未来切 Redis 只需替换 chatMemoryStore Bean。
 */
@Configuration
@RequiredArgsConstructor
public class MemoryConfig {

    private final ChatMemoryProperties props;

    @Bean
    public ChatMemoryStore chatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(props.getMaxMessages())
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
}
