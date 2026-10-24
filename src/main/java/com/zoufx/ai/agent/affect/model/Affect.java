package com.zoufx.ai.agent.affect.model;

import org.jspecify.annotations.Nullable;

/**
 * 用户情绪识别结果——分类器对"用户这句话"的判断，不是 AI 自己的情绪。
 *
 * @param emotion   6 词之一（枚举）
 * @param intensity 强度 1–5（1 很轻，5 强烈）
 */
public record Affect(Emotion emotion, int intensity) {

    public static Affect calm() {
        return new Affect(Emotion.CALM, 1);
    }

    public static Affect of(@Nullable String label, int intensity) {
        int i = Math.max(1, Math.min(5, intensity));
        return new Affect(Emotion.fromLabel(label), i);
    }
}
