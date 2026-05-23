package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 层主开关。仅承载当前激活的 profile 名。
 *
 * <p>v0.135 起替代旧的 {@code ai.llm.provider} 字段——profile 粒度比 protocol 更准确，
 * 同一 protocol（如 OpenAI 兼容）下不同厂商行为差异巨大（DeepSeek v4 always-on、智谱 GLM
 * 显式 thinking 开关、Qwen3 enable_thinking 等），按行为档位划分而非按 wire format。
 *
 * <p>profile 字段值在 {@code ai.llm.deepseek-v4.*} / {@code ai.llm.minimax.*} 等独立
 * Properties 命名空间中各自维护配置；模型装配由对应的 {@code XxxModelConfig} 通过
 * {@code @ConditionalOnProperty(name="ai.llm.profile", havingValue="...")} 路由激活。
 */
@Data
@ConfigurationProperties(prefix = "ai.llm")
public class LlmProperties {

    /** 当前激活的 profile：deepseek-v4 | minimax */
    private String profile;
}
