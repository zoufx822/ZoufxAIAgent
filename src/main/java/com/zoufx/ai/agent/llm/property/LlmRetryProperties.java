package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LLM 调用重试配置（v0.13 拆出，原 {@code ai.retry.llm} → {@code ai.llm.retry}）。
 *
 * <p>v0.13 命名空间重整：
 * <ul>
 *   <li>原 {@code chat/property/RetryProperties} 把 LLM retry 和 Tavily retry 混在
 *       同一类下（一个 yml 节 {@code ai.retry} 含 .llm 和 .tavily 两个子节）——
 *       跨模块耦合，新增工具 retry 时必须改这个公共类</li>
 *   <li>v0.13 改为==就近原则==：LLM retry 归 {@code llm/property/LlmRetryProperties}
 *       （prefix=ai.llm.retry），Tavily retry 内嵌到 {@code TavilySearchProperties}
 *       （prefix=ai.tools.web-search.tavily.retry）</li>
 *   <li>原 {@code chat/property/RetryProperties} 删除，{@code chat/property/} 子包不再有
 *       Properties 类——chat 是编排层，无独占 yml 节</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "ai.llm.retry")
public class LlmRetryProperties {

    private int maxAttempts = 2;
    private Duration minBackoff = Duration.ofMillis(500);
    private Duration maxBackoff = Duration.ofSeconds(2);
}
