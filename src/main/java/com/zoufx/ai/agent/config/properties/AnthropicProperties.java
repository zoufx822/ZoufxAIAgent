package com.zoufx.ai.agent.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Anthropic 兼容接口（MiniMax）配置。
 * 收口原 LangChain4JConfig 里 7 处 @Value，类型化嵌套结构。
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
