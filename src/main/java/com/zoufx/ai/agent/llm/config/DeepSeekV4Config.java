package com.zoufx.ai.agent.llm.config;

import com.zoufx.ai.agent.llm.model.Features;
import com.zoufx.ai.agent.llm.property.DeepSeekV4Properties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * DeepSeek v4 profile：把项目的三个模型角色映射到 DeepSeek 实现。
 *
 * <p>仅在 {@code ai.llm.profile.active=deepseek-v4} 时激活。配置来自 {@link DeepSeekV4Properties}。
 * 角色映射：思考档 = pro + thinking enabled + reasoning_effort max；快档 = flash + thinking disabled。
 *
 * <p>thinking 控制：{@code thinking.type} 是 DeepSeek 私有扩展字段（非标准 OpenAI 协议），
 * 通过 {@code OpenAiChatRequestParameters.customParameters} 注入请求体根级。
 * LC4J AiServices 不支持 per-call 参数覆盖，thinking 策略只能 builder 期固定。
 *
 * <p>returnThinking + sendThinking 全部 Bean 统一开启：思考档与快档共享同一份会话记忆
 * （按 anchorId 分桶），上一轮 pro 产出的 reasoning_content 必须在下一轮（即使走 flash）原样回传，
 * 否则 API 以 "reasoning_content must be passed back" 拒绝。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(DeepSeekV4Properties.class)
@ConditionalOnProperty(name = "ai.llm.profile.active", havingValue = "deepseek-v4")
public class DeepSeekV4Config {

    private final DeepSeekV4Properties props;

    /** DeepSeek 私有 thinking 字段，序列化为请求体根级 {@code "thinking": {"type": ...}}。 */
    private static Map<String, Object> thinkingParam(String type) {
        return Map.of("thinking", Map.of("type", type));
    }

    /** 思考模型（流式）：前端开启思考模式时的对话主路。 */
    @Bean("thinkingStreamingChatModel")
    public StreamingChatModel thinkingStreamingChatModel() {
        String model = props.getChat().getThinkingModel();
        log.info("Creating thinkingStreamingChatModel [deepseek-v4] model={} thinking=enabled effort=max", model);
        return streamingBuilder(model)
                .defaultRequestParameters(OpenAiChatRequestParameters.builder()
                        .reasoningEffort("max")
                        .customParameters(thinkingParam("enabled"))
                        .build())
                .build();
    }

    /** 快模型（流式）：前端关闭思考模式时的对话主路。 */
    @Bean("fastStreamingChatModel")
    public StreamingChatModel fastStreamingChatModel() {
        String model = props.getChat().getFastModel();
        log.info("Creating fastStreamingChatModel [deepseek-v4] model={} thinking=disabled", model);
        return streamingBuilder(model)
                .defaultRequestParameters(OpenAiChatRequestParameters.builder()
                        .customParameters(thinkingParam("disabled"))
                        .build())
                .build();
    }

    private OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder streamingBuilder(String model) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .modelName(model)
                .maxTokens(props.getChat().getMaxTokens())
                .timeout(props.getTimeout())
                .returnThinking(true)
                .sendThinking(true)
                // 测试期开启完整请求/响应日志，便于观察发给 LLM 的真实 JSON
                .logRequests(true)
                .logResponses(true);
    }

    /** 快模型（同步）：情绪快速分类 + 锚点摘要压缩，不参与流式聊天主路。 */
    @Bean("fastSyncChatModel")
    public ChatModel fastSyncChatModel() {
        String model = props.getChat().getFastModel();
        log.info("Creating fastSyncChatModel [deepseek-v4] model={} thinking=disabled", model);
        return OpenAiChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .modelName(model)
                .maxTokens(props.getChat().getMaxTokens())
                .timeout(props.getTimeout())
                .defaultRequestParameters(OpenAiChatRequestParameters.builder()
                        .customParameters(thinkingParam("disabled"))
                        .build())
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }

    @Bean
    public Features features() {
        return new Features("deepseek-v4");
    }
}
