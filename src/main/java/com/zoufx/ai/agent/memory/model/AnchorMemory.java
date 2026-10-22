package com.zoufx.ai.agent.memory.model;

import org.jspecify.annotations.Nullable;

/**
 * 锚点元数据——一次对话窗口的归属信息。
 *
 * <p>{@code summary} 是滚动摘要：由定时压缩扫描器对「空闲超阈值且有新内容」的锚点增量更新
 * （旧摘要 + 最近对话 → 更新后的摘要）。首次压缩前为 null；此后聊新内容会让摘要落后，下次扫描再增量重压。
 *
 * <p>{@code title} 创建时可为 null，{@code ChatService.persistTurn} 用首条 user 消息
 * 截取自动 backfill；前端 PATCH /ai/anchors/{anchorId}/title 也可无条件覆盖。
 *
 * <p>{@code lastExpression} 是本锚点最近一次显示的表情词（单个词）——供前端刷新后恢复表情，
 * 不存整轮轨迹。情绪如何产生、如何映射为表情由情绪链路决定，本字段只记录结果。
 */
public record AnchorMemory(
        String id,
        String userId,
        @Nullable String title,
        @Nullable String summary,
        @Nullable String lastExpression,
        long createdAt,
        long lastActiveAt) {
}
