package com.zoufx.ai.agent.llm.model;

/**
 * 当前激活 LLM profile 支持哪些功能 —— 前后端解耦的中间契约，经 {@code GET /ai/features} 透传。
 *
 * <p>各 profile 的 Config 装配本 Bean（{@code @ConditionalOnProperty} 保证同期仅一个），
 * {@code ChatController} 注入并透传；前端启动时拉一次缓存，profile 切换需重启。
 *
 * <p>当前 deepseek-v4 / minimax 两个 profile 的 thinkingToggle 均为 false：LC4J 1.13.1 的
 * langchain4j-anthropic 没有 AnthropicChatRequestParameters 子类，thinking 参数只能 builder 期固定，
 * 前端思考按钮统一仅控显示。等上游解除时，仅需把 Config 里的布尔值改 true——架构不动。
 *
 * @param profile        当前激活的 profile 名（如 "deepseek-v4" / "minimax"）
 * @param thinkingToggle 是否支持 thinking 开/关（false = always-on / 不支持 / 框架未暴露；
 *                       true = 可通过请求参数控制本轮是否思考）
 */
public record Features(
        String profile,
        boolean thinkingToggle
) {}
