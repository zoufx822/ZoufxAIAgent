package com.zoufx.ai.agent.tool.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "langchain4j.tavily-search")
public class TavilySearchProperties {

    private boolean enabled = true;

    private Tavily tavily = new Tavily();

    @Data
    public static class Tavily {
        private String apiKey;
        private String searchDepth = "basic";
        private boolean includeAnswer = true;
        private int maxResults = 5;
        private Duration timeout = Duration.ofSeconds(20);
    }
}
