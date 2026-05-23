package com.zoufx.ai.agent.chat.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 通用调用重试配置（prefix={@code ai.retry}）。
 *
 * <p>当前消费者为 {@link com.zoufx.ai.agent.chat.service.ChatService} 的 LLM 流主体重试。
 * 命名空间放顶层 {@code ai.retry} 而非嵌套在 {@code ai.llm.*} 下——retry 是运行时通用策略，
 * 多个 profile 共用同一份配置（与 api-key / model 等 per-profile 数据不同）。
 *
 * <p>Tavily 工具的 retry 仍内嵌在 {@code TavilySearchProperties} 里，体现"工具私有"语义；
 * 本类承载的是"主对话流通用"。
 */
@Data
@ConfigurationProperties(prefix = "ai.retry")
public class RetryProperties {

    private int maxAttempts = 2;
    private Duration minBackoff = Duration.ofMillis(500);
    private Duration maxBackoff = Duration.ofSeconds(2);
}
