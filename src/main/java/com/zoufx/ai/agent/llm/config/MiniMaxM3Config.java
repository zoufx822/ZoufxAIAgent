package com.zoufx.ai.agent.llm.config;

import com.zoufx.ai.agent.llm.model.Features;
import com.zoufx.ai.agent.llm.property.MiniMaxM3Properties;
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
 * MiniMax M3 profile：把项目的三个模型角色映射到 MiniMax 实现——三档同模型，靠 thinking 参数分档。
 *
 * <p>仅在 {@code ai.llm.profile.active=MiniMax-M3} 时激活。配置来自 {@link MiniMaxM3Properties}。
 * 走 Anthropic 兼容协议，profile 名对齐官方模型 ID（连的是 MiniMax 不是真 Claude）。
 * 角色映射（官方语义）：思考档 = {@code adaptive}（等同于开启 thinking）；快档 = {@code disabled}。
 * M3 默认关闭 thinking（与 M2.x 的无法关闭不同）。
 *
 * <p>returnThinking + sendThinking 全部 Bean 统一开启：思考档与快档共享同一份会话记忆，
 * 官方要求历史中的 thinking 内容块在后续轮次原样回传（尤其是工具调用对话）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MiniMaxM3Properties.class)
@ConditionalOnProperty(name = "ai.llm.profile.active", havingValue = "MiniMax-M3")
public class MiniMaxM3Config {

    private final MiniMaxM3Properties props;

    /** 思考模型（流式）：前端开启思考模式时的对话主路。 */
    @Bean("thinkingStreamingChatModel")
    public StreamingChatModel thinkingStreamingChatModel() {
        String model = props.getChat().getThinkingModel();
        log.info("Creating thinkingStreamingChatModel [MiniMax-M3] baseUrl={} model={} thinking=adaptive",
                props.getBaseUrl(), model);
        return streamingBuilder(model)
                .thinkingType("adaptive")
                .thinkingBudgetTokens(props.getThinking().getBudgetTokens())
                .build();
    }

    /** 快模型（流式）：显式关闭 thinking（M3 默认即关闭，显式声明防上游默认值漂移）。 */
    @Bean("fastStreamingChatModel")
    public StreamingChatModel fastStreamingChatModel() {
        String model = props.getChat().getFastModel();
        log.info("Creating fastStreamingChatModel [MiniMax-M3] model={} thinking=disabled", model);
        return streamingBuilder(model)
                .thinkingType("disabled")
                .build();
    }

    private AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder streamingBuilder(String model) {
        return AnthropicStreamingChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .version(props.getVersion())
                .modelName(model)
                .maxTokens(props.getChat().getMaxTokens())
                .timeout(props.getTimeout())
                .returnThinking(true)
                .sendThinking(true)
                .logRequests(true)
                .logResponses(true);
    }

    /** 快模型（同步）：情绪快速分类 + 锚点摘要压缩，不参与流式聊天主路。 */
    @Bean("fastSyncChatModel")
    public ChatModel fastSyncChatModel() {
        String model = props.getChat().getFastModel();
        log.info("Creating fastSyncChatModel [MiniMax-M3] model={} thinking=disabled", model);
        return AnthropicChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .version(props.getVersion())
                .modelName(model)
                .maxTokens(props.getChat().getMaxTokens())
                .timeout(props.getTimeout())
                .thinkingType("disabled")
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }

    @Bean
    public Features features() {
        return new Features("MiniMax-M3");
    }
}
