package com.zoufx.ai.agent.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 聊天请求 DTO。
 * userId 是后端记忆分区键（v0 起取代 sessionId）。前端 sidebar 的"多开聊天"概念仅作 UI 分组，不再传给后端。
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
     * 是否启用思考模式，默认 false
     */
    private boolean thinking = false;
}
