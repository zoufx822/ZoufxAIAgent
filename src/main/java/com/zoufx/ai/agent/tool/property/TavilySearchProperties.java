package com.zoufx.ai.agent.tool.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tavily 搜索引擎特定配置（v0.13 起 prefix 从 ai.web-search.tavily 迁到 ai.tools.web-search.tavily）。
 *
 * <p>v0.13 命名空间重整：
 * <ul>
 *   <li>外层 prefix 跟随 {@link WebSearchProperties} 迁到 {@code ai.tools.web-search.*} 子树</li>
 *   <li>新增 {@link Retry} 内嵌字段，承接原 {@code chat/property/RetryProperties.Tavily}——
 *       retry 配置==就近==放到被重试对象旁边，删除原跨模块的集中式 {@code ai.retry} 节</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "ai.tools.web-search.tavily")
public class TavilySearchProperties {

    private String apiKey;
    private String searchDepth = "basic";
    private boolean includeAnswer = true;
    private int maxResults = 5;
    private Duration timeout = Duration.ofSeconds(20);

    /** 调用失败时的重试策略（v0.13 从 {@code ai.retry.tavily} 内嵌进来）。 */
    private Retry retry = new Retry();

    @Data
    public static class Retry {
        private int maxAttempts = 2;
        private Duration backoff = Duration.ofMillis(500);
    }
}
