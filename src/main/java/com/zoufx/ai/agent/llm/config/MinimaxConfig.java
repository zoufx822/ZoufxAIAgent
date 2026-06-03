package com.zoufx.ai.agent.llm.config;

import com.zoufx.ai.agent.chat.api.LlmCapabilities;
import com.zoufx.ai.agent.llm.property.MinimaxProperties;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
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
 * <p>仅在 {@code ai.llm.profile=minimax} 时激活。配置来自 {@link MinimaxProperties}。
 * profile 名对齐实际产品而非协议——连的是 MiniMax 不是真 Claude。装配单一 {@code chatModel}，
 * builder 期固定开启 thinking，由 MiniMax M1/M2 自适应思考深度。
 *
 * <p>已知限制：LC4J 1.13.1 langchain4j-anthropic 未提供 AnthropicChatRequestParameters 子类，
 * thinking 参数无法 per-call 覆盖，因此 LlmCapabilities 声明 thinkingToggle=false（降级）。
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
        String model = props.getChat().getCoreModel();
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

    /**
     * 轻量同步 ChatModel——供 AnchorService 摘要压缩 + MoodService 情绪快速分类。不参与流式聊天主路。
     * MiniMax 无 flash 等轻量分层，此 bean 暂用与主模型同款 + builder 期开启 thinking（避免协议层
     * reasoning_content 回传报错），故"快"是尽力而为；上游放出更轻模型或可关 thinking 后再优化。
     */
    @Bean
    public ChatModel chatModelFast() {
        MinimaxProperties.Thinking thinking = props.getThinking();
        return AnthropicChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .version(props.getVersion())
                .modelName(props.getChat().getCoreModel())
                .maxTokens(props.getChat().getMaxTokens())
                .timeout(props.getTimeout())
                .thinkingType(thinking.getType())
                .thinkingBudgetTokens(thinking.getBudgetTokens())
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }

    @Bean
    public LlmCapabilities llmCapabilities() {
        // LC4J 1.13.1 langchain4j-anthropic 未暴露 per-call thinking 覆盖，降级声明 false；
        // 上游放出 AnthropicChatRequestParameters 后改回 (true, true) 即可，架构不动
        return new LlmCapabilities("minimax", false, false, false);
    }
}
