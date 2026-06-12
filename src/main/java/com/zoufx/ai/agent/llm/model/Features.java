package com.zoufx.ai.agent.llm.model;

/**
 * 当前激活 LLM profile 的标识声明，经 {@code GET /ai/features} 透传。
 *
 * <p>各 profile 的 Config 装配本 Bean（{@code @ConditionalOnProperty} 保证同期仅一个）。
 * 端点保留作为前端能力自适应的扩展点，当前前端不消费：LC4J 尚不支持 per-call 参数覆盖，
 * thinking 等维度做不到逐请求动态调整，声明给前端没有意义——上游解除后再扩展。
 *
 * @param profile 当前激活的 profile 名（与模型官方命名一致，如 "deepseek-v4" / "MiniMax-M3"）
 */
public record Features(String profile) {}
