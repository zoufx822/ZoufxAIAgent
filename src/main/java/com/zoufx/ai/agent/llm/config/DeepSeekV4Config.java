package com.zoufx.ai.agent.llm.config;

import com.zoufx.ai.agent.chat.api.LlmCapabilities;
import com.zoufx.ai.agent.llm.property.DeepSeekV4Properties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek v4 profile：装配 OpenAI 兼容协议下的 {@link StreamingChatModel} + {@link LlmCapabilities}。
 *
 * <p>仅在 {@code ai.llm.profile=deepseek-v4} 时激活。配置来自 {@link DeepSeekV4Properties}。
 * 装配单一 {@code chatModel}，thinking 行为由 v4 hybrid 模型自身自适应深度决定。
 *
 * <p>returnThinking + sendThinking 一起开：DeepSeek v4（v4-pro / v4-flash）即使非 thinking 入口
 * 也会产出 reasoning_content，多轮 / tool-call 后续请求若未把上一轮的 reasoning_content 回传，
 * 会被 API 以 "reasoning_content must be passed back" 拒绝。两个开关让 LC4J 在 message ⇄ API
 * 转换层自动 round-trip，无需碰序列化逻辑。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(DeepSeekV4Properties.class)
@ConditionalOnProperty(name = "ai.llm.profile", havingValue = "deepseek-v4")
public class DeepSeekV4Config {

    private final DeepSeekV4Properties props;

    @Bean
    public StreamingChatModel chatModel() {
        String model = props.getChat().getCoreModel();
        log.info("Creating chatModel [deepseek-v4] baseUrl={} model={}", props.getBaseUrl(), model);
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
                .logResponses(true)
                .build();
    }

    /**
     * 轻量同步 ChatModel——供 AnchorService 摘要压缩 + MoodService 情绪快速分类。不参与流式聊天主路。
     * 用 flash 模型（{@code fastModel}）抢延迟：情绪分类要在主流之前秒出，摘要是后台 fire-and-forget。
     * 单次调用无需 thinking 多轮回传，故略去 returnThinking/sendThinking。
     */
    @Bean
    public ChatModel chatModelFast() {
        return OpenAiChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .modelName(props.getChat().getFastModel())
                .maxTokens(props.getChat().getMaxTokens())
                .timeout(props.getTimeout())
                .build();
    }

    @Bean
    public LlmCapabilities llmCapabilities() {
        // DeepSeek v4 hybrid：always-on 自适应思考，协议层无 on/off 开关
        return new LlmCapabilities("deepseek-v4", false, false, false);
    }
}
