package com.zoufx.ai.agent.affect.model;

import org.jspecify.annotations.Nullable;

/**
 * 情绪词表——后端唯一事实源；JSON/DB 仍使用中文 label。
 */
public enum Emotion {

    CALM("平静"),
    HAPPY("愉快"),
    CURIOUS("好奇"),
    SAD("难过"),
    ANGRY("愤怒"),
    CONFUSED("困惑");

    private final String label;

    Emotion(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 中文 label → 枚举；非法/空回落 CALM。 */
    public static Emotion fromLabel(@Nullable String label) {
        if (label == null) return CALM;
        for (Emotion e : values()) {
            if (e.label.equals(label)) return e;
        }
        return CALM;
    }
}
