package com.zoufx.ai.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "ai.retry")
public class RetryProperties {

    private Llm llm = new Llm();
    private Tavily tavily = new Tavily();

    @Data
    public static class Llm {
        private int maxAttempts = 2;
        private Duration minBackoff = Duration.ofMillis(500);
        private Duration maxBackoff = Duration.ofSeconds(2);
    }

    @Data
    public static class Tavily {
        private int maxAttempts = 2;
        private Duration backoff = Duration.ofMillis(500);
    }
}
