package com.zoufx.ai.agent.prompt.impl;

import com.zoufx.ai.agent.prompt.api.PromptSection;
import com.zoufx.ai.agent.vector.support.RecallContextHolder;
import com.zoufx.ai.agent.vector.model.RecallResult;
import com.zoufx.ai.agent.vector.support.VectorPayload;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「## 此刻想起的相关记忆」段（order=45，system prompt 末段——置末保 prefix cache）。
 *
 * <p>内容由 {@code ChatService} 召回后预算好塞进 {@link RecallContextHolder}；本段只做一次同步
 * {@code Map.get}（符合 compose 同步契约，不阻塞）。召回内容每请求重算、绝不落 chat_memory 窗口。
 */
@Component
@RequiredArgsConstructor
public class RecallContextSection implements PromptSection {

    /** 单条内容截断上限（受限即驱动，控常驻注意力）。 */
    private static final int MAX_ITEM_LEN = 120;

    private final RecallContextHolder holder;

    @Override
    public int order() {
        return 45;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId, @Nullable String anchorId) {
        if (anchorId == null) return null;
        String block = holder.get(anchorId);
        return (block == null || block.isBlank()) ? null : block;
    }

    /**
     * 把召回结果渲染成段；空则返回 ""。由 ChatService 在 boundedElastic 上预算后塞 holder。
     * 条数已由召回 limit 控制，这里只做单条截断。
     */
    public static String format(List<RecallResult> hits) {
        if (hits == null || hits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("## 此刻想起的相关记忆\n\n");
        sb.append("（以下是你自然想起的、与当前对话相关的过往记忆——自然融入回应，不要生硬罗列或复述）\n");
        for (RecallResult r : hits) {
            sb.append("- ").append(label(r.memType())).append(truncate(r.content())).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String label(String memType) {
        // cold（对话原文）不加前缀；hot 三类加「[类型] 」
        return VectorPayload.COLD.equals(memType) ? "" : "[" + VectorPayload.labelOf(memType) + "] ";
    }

    private static String truncate(String s) {
        String t = s.replace("\n", " ").trim();
        return t.length() <= MAX_ITEM_LEN ? t : t.substring(0, MAX_ITEM_LEN) + "…";
    }
}
