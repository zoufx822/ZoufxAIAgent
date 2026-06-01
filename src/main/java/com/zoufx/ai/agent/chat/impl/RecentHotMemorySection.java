package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.model.HotMemoryEntry;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * append-only 类 hot_memory（significant-event / commitment）的注入段公共骨架——
 * 读最近 N 条按更新时间倒序渲染为 bullet 列表，无条目时跳过。
 * 子类只声明 {@link #type()} / {@link #header(int)} / {@link #formatItem(HotMemoryEntry, long)}。
 */
abstract class RecentHotMemorySection implements PromptSection {

    private static final int RECENT_INJECT_LIMIT = 5;

    protected final HotMemoryStore hotMemoryStore;

    protected RecentHotMemorySection(HotMemoryStore hotMemoryStore) {
        this.hotMemoryStore = hotMemoryStore;
    }

    /** 本段读取的 hot_memory type。 */
    protected abstract String type();

    /** 段首标题（含 count 与末尾两个换行）。 */
    protected abstract String header(int count);

    /** 单条记录渲染为一行 bullet（含末尾换行）。 */
    protected abstract String formatItem(HotMemoryEntry entry, long now);

    @Override
    @Nullable
    public String render(@Nullable String userId, @Nullable String anchorId) {
        if (userId == null) return null;
        List<HotMemoryEntry> entries = hotMemoryStore.recent(userId, type(), RECENT_INJECT_LIMIT);
        if (entries.isEmpty()) return null;

        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(header(entries.size()));
        for (HotMemoryEntry e : entries) {
            sb.append(formatItem(e, now));
        }
        sb.append("\n");
        return sb.toString();
    }
}
