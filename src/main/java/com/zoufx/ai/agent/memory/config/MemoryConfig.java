package com.zoufx.ai.agent.memory.config;

import com.zoufx.ai.agent.chat.property.ChatProps;
import com.zoufx.ai.agent.memory.api.ChatMemoryDao;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话记忆装配。
 * ChatMemoryDao 实现由 {@link com.zoufx.ai.agent.memory.impl.ChatMemoryDaoImpl} 提供（@Component 自动注入），
 * 本地接口继承 LC4J 的 ChatMemoryStore，可直接作为 MessageWindowChatMemory.builder().chatMemoryStore() 入参。
 * 未来切 Redis / Postgres 只需替换该 Bean。
 */
@Configuration
@RequiredArgsConstructor
public class MemoryConfig {

    private final ChatProps ChatProps;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryDao chatMemoryDao) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(ChatProps.getLoadMessage())
                .chatMemoryStore(chatMemoryDao)
                // 强制 system 消息位于 messages[0]：LC4J 默认 false 会把 system 追加到末尾，
                // 导致后续轮次 system 卡在历史中间，违反 OpenAI/Anthropic API 期望
                .alwaysKeepSystemMessageFirst(true)
                .build();
    }
}
