package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Anthropic 兼容协议配置（v0.13 起 prefix 从 spring.ai.anthropic 迁到 ai.llm.providers.anthropic）。
 *
 * <p>仅在 {@code ai.llm.provider=anthropic} 时启用，由 AnthropicModelConfig 装配。
 *
 * <p>v0.13 命名空间重整：
 * <ul>
 *   <li>原 {@code spring.ai.*} 借用了 Spring AI 官方命名空间——本项目并未引入 spring-ai
 *       依赖，未来若引入会与同前缀冲突。统一收回到本项目自有的 {@code ai.llm.providers.*}</li>
 *   <li>active provider 选择从 {@code ai.provider} 挪到 {@code ai.llm.provider}，与
 *       providers / retry 同层，表达"LLM 层内部事务"的归属</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "ai.llm.providers.anthropic")
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
