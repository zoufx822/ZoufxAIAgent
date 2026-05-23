package com.zoufx.ai.agent.llm.config;

import com.zoufx.ai.agent.chat.api.LlmCapabilities;
import com.zoufx.ai.agent.llm.property.MinimaxProperties;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MiniMax profile：装配 Anthropic 兼容协议下的 {@link StreamingChatModel} + {@link LlmCapabilities}。
 *
 * <p>仅在 {@code ai.llm.profile=minimax} 时激活（v0.135 起从 ai.llm.provider=anthropic 迁来）。
 * 配置来自 {@link MinimaxProperties}。profile 名对齐实际产品而非协议——连的是 MiniMax 不是真 Claude。
 *
 * <p>v0.135 重构：旧 {@code AnthropicModelConfig} 装配 thinkingChatModel + nonThinkingChatModel 双 Bean，
 * 与"消灭伪二分"目标冲突。本类装配单一 {@code chatModel}，builder 期固定开启 thinking——MiniMax M1/M2
 * 自适应思考深度。
 *
 * <p>v0.135 已知限制：LC4J 1.13.1 langchain4j-anthropic 未提供 AnthropicChatRequestParameters 子类，
 * thinking 参数无法 per-call 覆盖，因此 LlmCapabilities 暂声明 thinkingToggle=false（降级方案）。
 * 详见 总设计方案.md 的"已知技术债"章节。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MinimaxProperties.class)
@ConditionalOnProperty(name = "ai.llm.profile", havingValue = "minimax")
public class MinimaxConfig {

    private final MinimaxProperties props;

    @Bean
    public StreamingChatModel chatModel() {
        String model = props.getChat().getModel();
        MinimaxProperties.Thinking thinking = props.getThinking();
        log.info("Creating chatModel [minimax] baseUrl={} model={} thinking={}",
                props.getBaseUrl(), model, thinking.getType());
        return AnthropicStreamingChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .version(props.getVersion())
                .modelName(model)
                .maxTokens(props.getChat().getMaxTokens())
                .timeout(props.getTimeout())
                .thinkingType(thinking.getType())
                .thinkingBudgetTokens(thinking.getBudgetTokens())
                .returnThinking(true)
                .sendThinking(true)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public LlmCapabilities llmCapabilities() {
        // ⚠ v0.135 降级：LC4J 1.13.1 langchain4j-anthropic 未暴露 per-call thinking 覆盖
        // 等上游放出 AnthropicChatRequestParameters 后改回 (true, true)，架构不动
        return new LlmCapabilities("minimax", false, false, false);
    }
}
