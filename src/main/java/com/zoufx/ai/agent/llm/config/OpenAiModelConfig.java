package com.zoufx.ai.agent.llm.config;

import com.zoufx.ai.agent.llm.property.OpenAiProperties;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 兼容协议下的 StreamingChatModel 装配。
 *
 * 仅在 {@code ai.llm.provider=openai} 时激活（v0.13 起从 ai.provider 迁到 ai.llm.provider）。
 * 配置来自 {@link OpenAiProperties}。
 * thinking 模型开启 returnThinking，让 reasoning_content 通过 onPartialThinking 回调流出。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "openai")
public class OpenAiModelConfig {

    private final OpenAiProperties props;

    private OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder baseBuilder() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .maxTokens(props.getChat().getMaxTokens())
                .timeout(props.getTimeout())
                // 测试期开启完整请求/响应日志，便于观察发给 LLM 的真实 JSON
                .logRequests(true)
                .logResponses(true);
    }

    @Bean("thinkingChatModel")
    public StreamingChatModel thinkingChatModel() {
        String model = props.getChat().getThinkingModel();
        log.info("Creating thinkingChatModel [openai] baseUrl={} model={}", props.getBaseUrl(), model);
        return baseBuilder()
                .modelName(model)
                .returnThinking(true)
                .build();
    }

    @Bean("nonThinkingChatModel")
    public StreamingChatModel nonThinkingChatModel() {
        String model = props.getChat().getNonThinkingModel();
        log.info("Creating nonThinkingChatModel [openai] baseUrl={} model={}", props.getBaseUrl(), model);
        return baseBuilder()
                .modelName(model)
                .build();
    }
}
