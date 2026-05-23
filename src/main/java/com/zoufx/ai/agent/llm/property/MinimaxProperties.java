package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * MiniMax profile 配置（v0.135 起从 ai.llm.providers.anthropic 迁来）。
 *
 * <p>仅在 {@code ai.llm.profile=minimax} 时由 {@code MinimaxConfig} 装配读取。
 * 走 Anthropic 兼容协议（通过 LC4J {@code AnthropicStreamingChatModel} builder）连接
 * MiniMax 上游——profile 名对齐实际产品而非协议名，避免"Anthropic"误导（实际不连真 Claude）。
 *
 * <p>v0.135 命名修正与扁平化：旧 {@code AnthropicProperties} 名字承载了误导信息；
 * 旧嵌套结构 {@code chat.options.thinking.*} 太深，本次拍平为 {@code chat.* + thinking.*}。
 *
 * <p>关于 thinking：MiniMax M1/M2 走 Anthropic 协议，支持 thinking on/off + budget。
 * 但 LC4J 1.13.1 的 langchain4j-anthropic 未提供 {@code AnthropicChatRequestParameters}
 * 子类，per-call 时无法覆盖 thinking——本配置类的 thinking 段在 builder 期固定生效，
 * v0.135 降级方案下让 MiniMax 自适应思考深度（行为类似 DeepSeek v4 hybrid）。详见
 * {@code 拟人化AI Agent-总设计方案.md} 的"已知技术债"章节。
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
        /** 具体模型 ID，如 MiniMax-M2.5 */
        private String model;
        private int maxTokens = 16384;
    }

    @Data
    public static class Thinking {
        /** Anthropic 协议字段：enabled / disabled */
        private String type = "enabled";
        private int budgetTokens = 8192;
    }
}
