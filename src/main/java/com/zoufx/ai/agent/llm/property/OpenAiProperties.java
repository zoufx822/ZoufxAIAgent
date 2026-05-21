package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenAI 兼容协议配置（v0.13 起 prefix 从 spring.ai.openai 迁到 ai.llm.providers.openai）。
 *
 * <p>仅在 {@code ai.llm.provider=openai} 时由 OpenAiModelConfig 装配读取。
 * thinking 与 non-thinking 走不同模型名（OpenAI 协议下 reasoning 由模型本身决定）。
 *
 * <p>v0.13 命名空间重整理由：参见 {@link AnthropicProperties} javadoc。
 */
@Data
@ConfigurationProperties(prefix = "ai.llm.providers.openai")
public class OpenAiProperties {

    private String baseUrl;
    private String apiKey;
    private Duration timeout = Duration.ofSeconds(60);
    private Chat chat = new Chat();

    @Data
    public static class Chat {
        private String thinkingModel;
        private String nonThinkingModel;
        private int maxTokens = 4096;
    }
}
