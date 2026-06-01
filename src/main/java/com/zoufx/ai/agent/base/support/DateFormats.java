package com.zoufx.ai.agent.base.support;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 注入给 LLM / 检索结果的中文长日期格式（"2026 年 5 月 29 日"）——
 * system prompt 顶部「当前日期」与 search_web 结果「今日日期」共用，保证两处口径一致。
 */
public final class DateFormats {

    public static final DateTimeFormatter CN_LONG_DATE =
            DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA);

    private DateFormats() {
    }
}
