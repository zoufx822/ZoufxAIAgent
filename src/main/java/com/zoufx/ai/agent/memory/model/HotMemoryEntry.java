package com.zoufx.ai.agent.memory.model;

/**
 * Hot Memory 单条记录——纯数据载体，供 append-only type 的 recent 注入读取。
 *
 * <p>{@link #relativeTimeFrom(long)} 将 updatedAt 翻译为相对时间（"几天前"），
 * 给 LLM 时间感。updatedAt 是记录写入时刻，不是事件发生时刻。
 */
public record HotMemoryEntry(String key, String value, long updatedAt) {

    /**
     * 翻译为相对时间锚（刚刚 / N分钟前 / N小时前 / N天前 / N周前 / N个月前 / N年前）。
     * 月按 30 天、年按 365 天近似。now < updatedAt 兜底返回"刚刚"。
     */
    public String relativeTimeFrom(long now) {
        long elapsed = Math.max(0L, now - updatedAt);
        long minutes = elapsed / 60_000L;
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + " 分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + " 小时前";
        long days = hours / 24;
        if (days < 7) return days + " 天前";
        if (days < 30) return (days / 7) + " 周前";
        if (days < 365) return (days / 30) + " 个月前";
        return (days / 365) + " 年前";
    }
}
