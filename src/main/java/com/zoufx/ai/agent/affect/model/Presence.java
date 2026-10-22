package com.zoufx.ai.agent.affect.model;

import org.jspecify.annotations.Nullable;

/**
 * 页面要显示的存在状态——由用户情绪映射而来，是"共情反应"，不是 AI 自己的心情。
 *
 * @param expression 显示表情词（6 词之一）
 * @param intensity  1–5，前端据此决定 spotlight 大小/光晕/停留时长
 */
public record Presence(String expression, int intensity) {

    /**
     * 每轮都返回一个状态：中性/低强度也返回「平静」，避免上一轮的表情一直挂着。
     * 视觉是否放大由前端按 intensity 决定（<3 只更新 dock，不 spotlight）。
     */
    public static Presence from(@Nullable Affect affect) {
        if (affect == null) return new Presence("平静", 1);
        return new Presence(affect.emotion(), affect.intensity());
    }
}
