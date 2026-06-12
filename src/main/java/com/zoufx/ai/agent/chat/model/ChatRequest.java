package com.zoufx.ai.agent.chat.model;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * 聊天请求 DTO。
 *
 * <p>{@code anchorId} 非空时指向已存在的对话锚点；为空时表示新对话，Controller 自动创建。
 * {@code userId} 必传——后端不再反查 anchor→user 映射。
 *
 * <p>{@code prevAnchorId} 仅在客户端发生"锚点切换"时携带（即上一条消息所在锚点 ≠ 本次 anchorId）。
 * 后端据此 fire-and-forget 触发对前一锚点消息流的 LLM 摘要压缩，写入 anchor.summary 缓存。
 *
 * <p>{@code thinking} 是前端思考模式开关：true 走思考档 assistant、false/null 走快档。
 */
public record ChatRequest(
        @NotBlank(message = "不能为空") String prompt,
        @Nullable String anchorId,
        @Nullable String prevAnchorId,
        @Nullable Boolean thinking,
        @NotBlank(message = "不能为空") String userId) {
    public ChatRequest {
        if (thinking == null) thinking = false;
    }
}
