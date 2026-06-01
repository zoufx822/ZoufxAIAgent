package com.zoufx.ai.agent.chat.api;

/**
 * LLM 能力声明 —— 当前激活 profile 能做什么、不能做什么。前后端解耦的中间契约。
 *
 * <p>前端启动时 {@code GET /ai/capabilities} 拉一次缓存；profile 切换需要重启（后端联动）。
 * 各 profile 的 Config 装配 {@code LlmCapabilities} Bean，{@code ChatController} 注入并透传——
 * 同一启动期内仅一个 Bean 装配（{@code @ConditionalOnProperty} 保证）。
 *
 * <p>当前 deepseek-v4 / minimax 两个 profile 的 thinkingToggle / thinkingBudget 均为 false：
 * LC4J 1.13.1 的 langchain4j-anthropic 没有 AnthropicChatRequestParameters 子类，
 * thinking 参数只能 builder 期固定，前端按钮统一仅控显示。等上游解除时，仅需改 Config
 * 里 capability 声明的布尔值 + 前端按 capability 决定是否在请求体带 thinking 字段——架构不动。
 *
 * @param profile 当前激活的 profile 名（如 "deepseek-v4" / "minimax"）
 * @param thinkingToggle 是否支持 thinking 开/关（false = LLM always-on / 不支持思考 / 框架未暴露；
 *                       true = 可通过请求参数控制本轮是否思考）
 * @param thinkingBudget 是否支持 thinking 预算调节（false = 不可控；true = 可通过参数控制）
 * @param reasoningEffort 是否支持 reasoning_effort 档位（low/medium/high；OpenAI o-series 风格）
 */
public record LlmCapabilities(
        String profile,
        boolean thinkingToggle,
        boolean thinkingBudget,
        boolean reasoningEffort
) {}
