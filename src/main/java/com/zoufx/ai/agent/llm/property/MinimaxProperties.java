package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * MiniMax profile 配置。仅在 {@code ai.llm.profile=minimax} 时由 {@code MinimaxConfig} 装配读取。
 * 走 Anthropic 兼容协议（通过 LC4J {@code AnthropicStreamingChatModel} builder）连接
 * MiniMax 上游——profile 名对齐实际产品而非协议名（实际不连真 Claude）。
 *
 * <p>关于 thinking：MiniMax 走 Anthropic 协议本支持 thinking on/off + budget，但 LC4J 1.13.1
 * langchain4j-anthropic 未提供 {@code AnthropicChatRequestParameters} 子类，无法 per-call 覆盖——
 * thinking 段在 builder 期固定生效，由 MiniMax 自适应思考深度。
 */
@Data
@ConfigurationProperties(prefix = "ai.llm.minimax")
public class MinimaxProperties {

    private String baseUrl;
    private String apiKey;
    /** Anthropic 协议 API 版本头 */
    private String version = "2023-06-01";
    private Duration timeout = Duration.ofSeconds(60);
    private Chat chat = new Chat();
    private Thinking thinking = new Thinking();

    @Data
    public static class Chat {
        /** 核心模型 ID（流式主聊天），如 MiniMax-M2.5 */
        private String coreModel;
        private int maxTokens = 16384;
    }

    @Data
    public static class Thinking {
        /** Anthropic 协议字段：enabled / disabled */
        private String type = "enabled";
        private int budgetTokens = 8192;
    }
}
