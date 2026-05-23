package com.zoufx.ai.agent.llm.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * DeepSeek v4 profile 配置（v0.135 起从 ai.llm.providers.openai 迁来）。
 *
 * <p>仅在 {@code ai.llm.profile=deepseek-v4} 时由 {@code DeepSeekV4Config} 装配读取。
 * 走 OpenAI 兼容协议（通过 LC4J {@code OpenAiStreamingChatModel} builder），但 profile
 * 名表达"对接 DeepSeek v4 系列"这个事实——pro / flash 内部细分由 {@link Chat#model} 字段决定。
 *
 * <p>v0.135 命名修正：旧 {@code OpenAiProperties} 名字承载了误导信息（不是真 OpenAI），
 * 借本次 LLM 装配层重构改名 + 命名空间扁平化（去掉 providers 中间层），让"profile 名 =
 * yml 命名空间"完全对应，无需翻译。
 *
 * <p>关于 thinking：DeepSeek v4 是 hybrid 模型，always-on 自适应深度，协议层无 on/off 开关。
 * 配置层不暴露 thinking 字段；returnThinking / sendThinking 在 ModelConfig 中固定开启
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
        /** 具体模型 ID，如 deepseek-v4-pro / deepseek-v4-flash */
        private String model;
        private int maxTokens = 4096;
    }
}
