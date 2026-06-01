package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.support.HotMemoryType;
import com.zoufx.ai.agent.memory.model.HotMemoryEntry;
import org.springframework.stereotype.Component;

/**
 * 「## 你与对方共享的重要经历」段（order=22）。
 */
@Component
public class SignificantEventSection extends RecentHotMemorySection {

    public SignificantEventSection(HotMemoryStore hotMemoryStore) {
        super(hotMemoryStore);
    }

    @Override
    public int order() {
        return 22;
    }

    @Override
    protected String type() {
        return HotMemoryType.SIGNIFICANT_EVENT;
    }

    @Override
    protected String header(int count) {
        return "## 你与对方共享的重要经历（最近 " + count + " 条，括号内为对方提到的时间）\n\n";
    }

    @Override
    protected String formatItem(HotMemoryEntry entry, long now) {
        return "- (" + entry.relativeTimeFrom(now) + "提到) " + entry.value() + "\n";
    }
}
