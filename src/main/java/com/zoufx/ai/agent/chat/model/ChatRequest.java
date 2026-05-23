package com.zoufx.ai.agent.chat.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 聊天请求 DTO。
 * userId 是后端记忆分区键（v0.01 起取代 sessionId）。前端 sidebar 的"多开聊天"概念仅作 UI 分组，不再传给后端。
 *
 * 入参校验：null / 全空白 都会被 @NotBlank 拦截，由 GlobalExceptionHandler 翻译为 HTTP 400。
 */
@Data
public class ChatRequest {

    @NotBlank(message = "不能为空")
    private String prompt;

    @NotBlank(message = "不能为空")
    private String userId;

    /**
     * 用户希望本轮 LLM 思考（v0.135 起语义重定义）。
     *
     * <p>v0.13 及之前的语义为"路由到 thinkingAssistant 还是 nonThinkingAssistant"（实现细节泄漏）；
     * v0.135 合并双 Assistant 后，本字段重定义为==用户意图==：true 表示希望本轮模型深思考。
     *
     * <p>当前激活 profile 的 capability（{@code GET /ai/capabilities}）决定本字段是否生效：
     * {@code thinkingToggle=false} 时被后端 {@code ChatService} 静默忽略 + warn log，不报 400。
     * 前端可按 capability 决定是否在请求体携带本字段。
     */
    private boolean thinking = false;
}
