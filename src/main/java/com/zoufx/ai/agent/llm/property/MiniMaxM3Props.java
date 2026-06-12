package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * MiniMax M3 profile 配置。仅在 {@code ai.llm.profile.active=MiniMax-M3} 时由
 * {@code MiniMaxM3Config} 装配读取。走 Anthropic 兼容协议（LC4J Anthropic builder 对
 * thinking 有一等支持），连接 MiniMax 上游。
 *
 * <p>thinking 档位（adaptive/disabled）是架构固定值（按业务角色分 Bean），不进配置；
 * 仅思考预算 token 数可调。
 */
@Data
@ConfigurationProperties(prefix = "ai.llm.minimax-m3")
public class MiniMaxM3Props {

    private String baseUrl;
    private String apiKey;
    /** Anthropic 协议 API 版本头 */
    private String version = "2023-06-01";
    private Duration timeout = Duration.ofSeconds(60);
    private Chat chat = new Chat();
    private Thinking thinking = new Thinking();

    @Data
    public static class Chat {
        /** 思考档模型 ID。M3 无快慢模型分层，当前与 fastModel 同值，靠 thinking 参数分档 */
        private String thinkingModel;
        /** 快档模型 ID。上游放出快速变体后只改这一行 */
        private String fastModel;
        private int maxTokens = 16384;
    }

    @Data
    public static class Thinking {
        /** adaptive 档的思考预算 token 数 */
        private int budgetTokens = 8192;
    }
}
