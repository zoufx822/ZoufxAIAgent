package com.zoufx.ai.agent.tool.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tavily 搜索引擎特定配置。
 * 前缀 ai.web-search.tavily 下的配置项。
 */
@Data
@ConfigurationProperties(prefix = "ai.web-search.tavily")
public class TavilySearchProperties {

    private String apiKey;
    private String searchDepth = "basic";
    private boolean includeAnswer = true;
    private int maxResults = 5;
    private Duration timeout = Duration.ofSeconds(20);
}
