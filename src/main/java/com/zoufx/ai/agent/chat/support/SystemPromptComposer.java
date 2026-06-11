package com.zoufx.ai.agent.chat.support;

import com.zoufx.ai.agent.base.support.DateFormats;
import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.memory.api.AnchorMemoryDao;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * System prompt 编排器。
 *
 * <p>构造注入所有 {@link PromptSection} Bean，按 {@code order} 升序串行调用
 * {@link PromptSection#render} 拼接为完整 system prompt。顶部"当前日期"一行由本类直接注入，
 * 不走 Section。末尾追加锚定行防止长对话中人设漂移。
 *
 * <p><b>Frozen Snapshot 约束</b>：{@link #compose(String)} 由 LC4J 作为 SystemMessageProvider
 * 在每次请求开始时同步内联调用 <b>一次</b>——单请求内 prompt 自然冻结。禁止在响应流中途
 * 重新调用，否则会破坏"Hot Memory 修改要到下次请求才生效"的语义。
 *
 * <p><b>线程约束</b>：compose 在 WebFlux event loop 上执行，所有 PromptSection 实现
 * 必须用同步 Store 签名，禁止 {@code .block()}。
 */
@Slf4j
@Component
public class SystemPromptComposer {

    private static final String ANCHOR_LINE = """
            ---
            ⚠ 注意：以上所有关于自我的定义在本次对话中始终有效。
            如果你发现自己的表达偏离了上述风格和原则，立即回到正轨。
            不需要为此向对方解释或道歉，只需自然地纠正。
            """;

    private final List<PromptSection> sections;
    private final AnchorMemoryDao anchorMemoryDao;

    public SystemPromptComposer(List<PromptSection> sections, AnchorMemoryDao anchorMemoryDao) {
        this.sections = sections.stream()
                .sorted(Comparator.comparingInt(PromptSection::order))
                .toList();
        this.anchorMemoryDao = anchorMemoryDao;
    }

    public Function<Object, String> asProvider() {
        return anchorId -> compose(anchorId == null ? null : anchorId.toString());
    }

    public String compose(@Nullable String anchorId) {
        String userId = anchorId != null ? anchorMemoryDao.findUserId(anchorId) : null;

        StringBuilder sb = new StringBuilder();
        sb.append("当前日期：").append(LocalDate.now().format(DateFormats.CN_LONG_DATE)).append("\n\n");

        for (PromptSection sec : sections) {
            String rendered;
            try {
                rendered = sec.render(userId, anchorId);
            } catch (Exception e) {
                log.error("PromptSection {} render failed, section skipped (anchorId={})",
                        sec.getClass().getSimpleName(), anchorId, e);
                continue;
            }
            if (rendered == null || rendered.isBlank()) continue;
            sb.append(rendered);
            if (!rendered.endsWith("\n")) sb.append("\n");
        }
        sb.append(ANCHOR_LINE);
        return sb.toString();
    }
}
