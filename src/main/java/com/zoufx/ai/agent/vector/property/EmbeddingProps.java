package com.zoufx.ai.agent.vector.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Embedding 模型配置（OpenAI 兼容协议，BGE-M3）。
 *
 * <p>恒走 OpenAI 兼容 endpoint，与当前激活的 {@code ai.llm.profile} 无关——
 * profile 切到 minimax（Anthropic 协议）时 embedding 仍走自己的 endpoint。
 */
@Data
@ConfigurationProperties(prefix = "ai.embedding")
public class EmbeddingProps {

    private String baseUrl;
    private String apiKey;
    private String model;
    /** 输出维度，必须与 {@code ai.vector.dimension} 及 collection 声明一致。 */
    private int dimension;
    private Duration timeout = Duration.ofSeconds(30);
}
