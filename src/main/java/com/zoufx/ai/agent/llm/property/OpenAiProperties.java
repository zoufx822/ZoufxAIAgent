package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenAI 兼容协议配置。
 * 仅在 ai.provider=openai 时由 OpenAiModelConfig 装配读取。
 * thinking 与 non-thinking 走不同模型名（OpenAI 协议下 reasoning 由模型本身决定）。
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.openai")
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
