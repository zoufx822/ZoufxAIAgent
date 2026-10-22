package com.zoufx.ai.agent.affect.model;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * 用户情绪识别结果——分类器对"用户这句话"的判断，不是 AI 自己的情绪。
 *
 * @param emotion   6 词之一：平静 / 愉快 / 好奇 / 难过 / 愤怒 / 困惑
 * @param intensity 强度 1–5（1 很轻，5 强烈）
 */
public record Affect(String emotion, int intensity) {

    public static final Set<String> EMOTIONS = Set.of("平静", "愉快", "好奇", "难过", "愤怒", "困惑");

    public static Affect calm() {
        return new Affect("平静", 1);
    }

    public static Affect of(@Nullable String emotion, int intensity) {
        String e = emotion != null && EMOTIONS.contains(emotion) ? emotion : "平静";
        int i = Math.max(1, Math.min(5, intensity));
        return new Affect(e, i);
    }
}
