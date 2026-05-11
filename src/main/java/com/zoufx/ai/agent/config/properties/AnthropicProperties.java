package com.zoufx.ai.agent.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Anthropic 兼容协议配置。
 * 仅在 ai.provider=anthropic 时启用，由 AnthropicModelConfig 装配。
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.anthropic")
public class AnthropicProperties {

    private String baseUrl;
    private String apiKey;
    private String version = "2023-06-01";
    private Duration timeout = Duration.ofSeconds(60);
    private Chat chat = new Chat();

    @Data
    public static class Chat {
        private Options options = new Options();
    }

    @Data
    public static class Options {
        private String model;
        private int maxTokens = 4096;
        private Thinking thinking = new Thinking();
    }

    @Data
    public static class Thinking {
        private String type = "enabled";
        private int budgetTokens = 2048;
    }
}
