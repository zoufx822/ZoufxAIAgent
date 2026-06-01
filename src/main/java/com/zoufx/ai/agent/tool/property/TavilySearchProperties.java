package com.zoufx.ai.agent.tool.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tavily 搜索引擎特定配置（prefix={@code ai.tools.web-search.tavily}）。
 * retry 配置就近内嵌在被重试对象旁边。
 */
@Data
@ConfigurationProperties(prefix = "ai.tools.web-search.tavily")
public class TavilySearchProperties {

    private String apiKey;
    private String searchDepth = "basic";
    private boolean includeAnswer = true;
    private int maxResults = 5;
    private Duration timeout = Duration.ofSeconds(20);

    /** 调用失败时的重试策略。 */
    private Retry retry = new Retry();

    @Data
    public static class Retry {
        private int maxAttempts = 2;
        private Duration backoff = Duration.ofMillis(500);
    }
}
