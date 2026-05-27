package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.support.HotMemoryType;
import com.zoufx.ai.agent.memory.model.HotMemoryEntry;
import com.zoufx.ai.agent.memory.property.MemoryProperties;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「## 你与对方共享的重要经历」段（order=22）——读 hot_memory significant-event 的
 * 最近 N 条，按更新时间倒序渲染为 bullet 列表。无条目时跳过。
 */
@Component
@RequiredArgsConstructor
public class SignificantEventSection implements PromptSection {

    private final HotMemoryStore hotMemoryStore;
    private final MemoryProperties properties;

    @Override
    public int order() {
        return 22;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId, @Nullable String anchorId) {
        if (userId == null) return null;
        int limit = properties.getHot().getSignificantEvent().getRecentInjectLimit();
        if (limit <= 0) return null;
        List<HotMemoryEntry> events = hotMemoryStore.recent(userId, HotMemoryType.SIGNIFICANT_EVENT, limit);
        if (events.isEmpty()) return null;

        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("## 你与对方共享的重要经历（最近 ")
                .append(events.size())
                .append(" 条，括号内为对方提到的时间）\n\n");
        for (HotMemoryEntry e : events) {
            sb.append("- (").append(e.relativeTimeFrom(now)).append("提到) ")
                    .append(e.value()).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}
