package com.zoufx.ai.agent.chat.model;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * 聊天请求 DTO。
 *
 * <p>{@code anchorId} 是后端记忆分区键，一个 anchor = 一次对话窗口；
 * userId 由 {@code AnchorMemoryStore.findUserId(anchorId)} 反查。
 *
 * <p>{@code prevAnchorId} 仅在客户端发生"锚点切换"时携带（即上一条消息所在锚点 ≠ 本次 anchorId）。
 * 后端据此 fire-and-forget 触发对前一锚点消息流的 LLM 摘要压缩，写入 anchor.summary 缓存。
 *
 * <p>{@code thinking} 用 Boolean 包装类型——原始 boolean 在 JSON 缺失时
 * 会让 Jackson 直接抛 400 Bad Request（早于 validation），compact constructor 设默认 false 兼容旧客户端。
 */
public record ChatRequest(
        @NotBlank(message = "不能为空") String prompt,
        @NotBlank(message = "不能为空") String anchorId,
        @Nullable String prevAnchorId,
        @Nullable Boolean thinking) {
    public ChatRequest {
        if (thinking == null) thinking = false;
    }
}
