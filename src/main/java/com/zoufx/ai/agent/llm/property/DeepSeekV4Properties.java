package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * DeepSeek v4 profile 配置。仅在 {@code ai.llm.profile=deepseek-v4} 时由 {@code DeepSeekV4Config}
 * 装配读取。走 OpenAI 兼容协议（通过 LC4J {@code OpenAiStreamingChatModel} builder），
 * pro / flash 内部细分由 {@link Chat#model} 字段决定。
 *
 * <p>关于 thinking：DeepSeek v4 是 hybrid 模型，always-on 自适应深度，协议层无 on/off 开关。
 * 配置层不暴露 thinking 字段；returnThinking / sendThinking 在 Config 中固定开启
 * （多轮 / tool-call 后续请求需把 reasoning_content 原样回传，否则 API 拒绝）。
 */
@Data
@ConfigurationProperties(prefix = "ai.llm.deepseek-v4")
public class DeepSeekV4Properties {

    private String baseUrl;
    private String apiKey;
    private Duration timeout = Duration.ofSeconds(60);
    private Chat chat = new Chat();

    @Data
    public static class Chat {
        /** 核心模型 ID（流式主聊天），如 deepseek-v4-pro */
        private String coreModel;
        /** 轻量辅助模型（摘要压缩 / 情绪快速分类）——用 flash 抢延迟，这两个场景对质量要求低、对延迟敏感 */
        private String fastModel = "deepseek-v4-flash";
        private int maxTokens = 4096;
    }
}
