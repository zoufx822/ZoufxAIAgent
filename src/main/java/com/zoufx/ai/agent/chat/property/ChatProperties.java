package com.zoufx.ai.agent.chat.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Chat 层配置（prefix=ai.chat）。
 */
@Data
@ConfigurationProperties(prefix = "ai.chat")
public class ChatProperties {

    private Retry retry = new Retry();

    @Data
    public static class Retry {
        /** LLM 流调用最大重试次数 */
        private int maxAttempts = 2;
        private Duration minBackoff = Duration.ofMillis(500);
        private Duration maxBackoff = Duration.ofSeconds(2);
    }
}
