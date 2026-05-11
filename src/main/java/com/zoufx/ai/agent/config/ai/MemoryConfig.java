package com.zoufx.ai.agent.config.ai;

import com.zoufx.ai.agent.config.properties.ChatMemoryProperties;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话记忆装配。
 * ChatMemoryStore 实现由 {@link com.zoufx.ai.agent.memory.SqliteChatMemoryStore} 提供（@Component 自动注入），
 * v0 起切到 SQLite 持久化；未来切 Redis / Postgres 只需替换该 Bean。
 */
@Configuration
@RequiredArgsConstructor
public class MemoryConfig {

    private final ChatMemoryProperties props;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(props.getMaxMessages())
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
}
