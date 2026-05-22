package com.zoufx.ai.agent.chat.api;

import org.jspecify.annotations.Nullable;

/**
 * System prompt 的可组合「段」（v0.13 引入）。
 *
 * <p>各业务模块自建实现 Bean，由 {@code SystemPromptComposer} 按 {@link #order()}
 * 升序串行调 {@link #render(String)} 拼接。Composer 退化为编排器，业务逻辑全部内聚到各 Section。
 *
 * <p>归属：所有 PromptSection 实现一律落 chat/impl/。Section 本质是 chat 编排器的输入，
 * 业务模块只通过 api/property 暴露数据源（如 SoulStore / HotMemoryStore / ToolPrompt），
 * "如何把数据拼成 prompt 段"的知识集中在 chat 编排层，不下放到各业务模块。
 *
 * <p><b>契约</b>：
 * <ul>
 *   <li>{@link #render(String)} 由 LC4J 作为 SystemMessageProvider 在 ==WebFlux event loop 上同步内联==调用——
 *       禁止 {@code .block()} 反应式 Mono；所有 Store 读必须用同步签名</li>
 *   <li>返回 null / 空串 表示本段在当前请求跳过（如 SOUL 在 enabled-keys 为空时跳过；
 *       Identity 在 user-impression 字段全空时跳过）</li>
 *   <li>实现必须 ==无状态==——多请求复用同一 Bean，状态藏在注入的 Store 里</li>
 * </ul>
 *
 * <p>Frozen Snapshot 语义由编排器（SystemPromptComposer）一次性保证——单请求开始时
 * compose() 同步调一次所有 Section.render()，render 期间的 hot/cold/soul 修改不影响本轮。
 */
public interface PromptSection {

    /** 注入顺序（升序）。建议保留间隔（如 10/20/30/...）方便未来插入新 Section。 */
    int order();

    /**
     * 渲染本段内容。
     *
     * @param memoryId LC4J 透传的 memoryId（本项目语义即 userId）。可能为 null（无身份上下文场景）。
     * @return 段内容，已包含必要的换行；null / 空串表示本段在当前请求跳过。
     */
    @Nullable String render(@Nullable String memoryId);
}
