package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.support.HotMemoryType;
import com.zoufx.ai.agent.memory.model.HotMemoryEntry;
import org.springframework.stereotype.Component;

/**
 * 「## 双方做出的承诺」段（order=24）——承诺方靠 value 文本前缀区分，段内不额外标注。
 */
@Component
public class CommitmentSection extends RecentHotMemorySection {

    public CommitmentSection(HotMemoryStore hotMemoryStore) {
        super(hotMemoryStore);
    }

    @Override
    public int order() {
        return 24;
    }

    @Override
    protected String type() {
        return HotMemoryType.COMMITMENT;
    }

    @Override
    protected String header(int count) {
        return "## 双方做出的承诺（最近 " + count + " 条，括号内为承诺达成时间）\n\n";
    }

    @Override
    protected String formatItem(HotMemoryEntry entry, long now) {
        return "- (" + entry.relativeTimeFrom(now) + ") " + entry.value() + "\n";
    }
}
