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
 * 「## 双方做出的承诺」段（order=24）——读 hot_memory commitment 的最近 N 条，
 * 按更新时间倒序渲染为 bullet 列表。承诺方靠 value 文本前缀区分，段内不额外标注。
 * 无条目时跳过。
 */
@Component
@RequiredArgsConstructor
public class CommitmentSection implements PromptSection {

    private final HotMemoryStore hotMemoryStore;
    private final MemoryProperties properties;

    @Override
    public int order() {
        return 24;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId) {
        if (userId == null) return null;
        int limit = properties.getHot().getCommitment().getRecentInjectLimit();
        if (limit <= 0) return null;
        List<HotMemoryEntry> commitments = hotMemoryStore.recent(userId, HotMemoryType.COMMITMENT, limit);
        if (commitments.isEmpty()) return null;

        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("## 双方做出的承诺（最近 ")
                .append(commitments.size())
                .append(" 条，括号内为承诺达成时间）\n\n");
        for (HotMemoryEntry c : commitments) {
            sb.append("- (").append(c.relativeTimeFrom(now)).append(") ")
                    .append(c.value()).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}
