package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.memory.api.AnchorMemoryDao;
import com.zoufx.ai.agent.memory.model.AnchorMemoryEntry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「## 你与对方的其他对话窗口」段（order=28）——把同一用户的其他锚点摘要注入当前会话，
 * 让 AI 在跨窗口语境下保有连续记忆。
 *
 * <p>只渲染已有 summary 的锚点（即客户端切走时已被 {@code AnchorService} 压缩过的）。
 * 当前活跃锚点 summary=null 会被 touch 清空——避免把"正在进行"的对话当背景塞回自己。
 * 全部锚点 summary 都为空时跳过本段。
 */
@Component
@RequiredArgsConstructor
public class AnchorContextSection implements PromptSection {

    /** 最多注入的其他锚点条数——按 last_active_at desc 取头部。 */
    private static final int INJECT_LIMIT = 5;

    private final AnchorMemoryDao anchorMemoryDao;

    @Override
    public int order() {
        return 28;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId, @Nullable String anchorId) {
        if (userId == null || anchorId == null) return null;

        List<AnchorMemoryEntry> others = anchorMemoryDao.listOtherAnchors(userId, anchorId);
        if (others.isEmpty()) {
            return "## 你与对方的其他对话窗口\n\n当前没有其他对话窗口的摘要——**不要**凭空说「我们之前聊过 X」或「你之前提到过 Y」。\n\n";
        }

        StringBuilder sb = new StringBuilder();
        int rendered = 0;
        for (AnchorMemoryEntry a : others) {
            if (rendered >= INJECT_LIMIT) break;
            String summary = a.summary();
            if (summary == null || summary.isBlank()) continue;
            if (rendered == 0) {
                sb.append("## 你与对方的其他对话窗口（作为背景参考，已按最近活跃排序）\n\n");
            }
            String title = a.title();
            if (title == null || title.isBlank()) title = "未命名对话";
            sb.append("- 「").append(title).append("」：").append(summary).append("\n");
            rendered++;
        }
        if (rendered == 0) {
            return "## 你与对方的其他对话窗口\n\n当前没有其他对话窗口的摘要——**不要**凭空说「我们之前聊过 X」或「你之前提到过 Y」。\n\n";
        }
        sb.append("\n");
        return sb.toString();
    }
}
