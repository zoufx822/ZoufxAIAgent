package com.zoufx.ai.agent.config.ai;

import com.zoufx.ai.agent.config.properties.AnthropicProperties;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 流式 ChatModel 装配。
 *
 * 配置来自 {@link AnthropicProperties}（spring.ai.anthropic.*）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ModelConfig {

    private final AnthropicProperties props;

    private AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder baseBuilder() {
        AnthropicProperties.Options opts = props.getChat().getOptions();
        return AnthropicStreamingChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .version(props.getVersion())
                .modelName(opts.getModel())
                .maxTokens(opts.getMaxTokens())
                .timeout(props.getTimeout());
    }

    @Bean("thinkingChatModel")
    public StreamingChatModel thinkingChatModel() {
        log.info("Creating thinkingChatModel with baseUrl: {}", props.getBaseUrl());
        AnthropicProperties.Thinking thinking = props.getChat().getOptions().getThinking();
        return baseBuilder()
                .thinkingType(thinking.getType())
                .thinkingBudgetTokens(thinking.getBudgetTokens())
                .returnThinking(true)
                .build();
    }

    @Bean("nonThinkingChatModel")
    public StreamingChatModel nonThinkingChatModel() {
        log.info("Creating nonThinkingChatModel with baseUrl: {}", props.getBaseUrl());
        return baseBuilder().build();
    }
}
