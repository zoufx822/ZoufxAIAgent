package com.zoufx.ai.agent.chat.model;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * 聊天请求 DTO。
 *
 * <p>{@code anchorId} 是 v0.145 起的后端记忆分区键（取代 v0.01-v0.14 的 userId）——
 * 一个 anchor = 一次对话窗口；userId 由 {@code AnchorMemoryStore.findUserId(anchorId)} 反查。
 *
 * <p>{@code prevAnchorId} 仅在客户端发生"锚点切换"时携带（即上一条消息所在锚点 ≠ 本次 anchorId）。
 * 后端据此 fire-and-forget 触发对前一锚点消息流的 LLM 摘要压缩，写入 anchor.summary 缓存。
 *
 * <p>入参校验：null / 全空白 都会被 @NotBlank 拦截，由 GlobalExceptionHandler 翻译为 HTTP 400。
 */
public record ChatRequest(
        @NotBlank(message = "不能为空") String prompt,
        @NotBlank(message = "不能为空") String anchorId,
        @Nullable String prevAnchorId,
        boolean thinking) {
}
