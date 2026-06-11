package com.zoufx.ai.agent.mood.support;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * 情绪词谱的单一事实源——快速分类校验与提示词共用，与前端 eyes.tsx 的表情预设一一对应。
 * 扩词必须同步前端预设，故集合在此收口。
 */
public final class Moods {

    /** 兜底情绪：LLM 输出非法 / 乱码时回落到此。 */
    public static final String CALM = "平静";

    /** 默认层级正面情绪：比平静多一分暖意，比兴奋少一分激烈；LLM 拿不准时优先选用。 */
    public static final String JOLLY = "愉快";

    public static final List<String> ALL = List.of(CALM, JOLLY, "兴奋", "难过", "愤怒", "好奇", "困惑");

    private static final Set<String> SET = Set.copyOf(ALL);

    private Moods() {}

    public static boolean isValid(@Nullable String s) {
        return s != null && SET.contains(s.trim());
    }

    /** 从 LLM 原始输出提取合法情绪词：精确等值优先，否则取首个被包含的词，都没有则回落「平静」。 */
    public static String normalize(@Nullable String raw) {
        if (raw == null) return CALM;
        String t = raw.trim();
        if (SET.contains(t)) return t;
        for (String w : ALL) {
            if (t.contains(w)) return w;
        }
        return CALM;
    }
}
