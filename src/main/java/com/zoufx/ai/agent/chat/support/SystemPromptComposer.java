package com.zoufx.ai.agent.chat.support;

import com.zoufx.ai.agent.chat.api.PromptSection;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * System prompt 编排器。
 *
 * <p>构造注入所有 {@link PromptSection} Bean，按 {@code order} 升序串行调用
 * {@link PromptSection#render} 拼接为完整 system prompt。顶部"当前日期"一行由本类直接注入，
 * 不走 Section。
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

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA);

    private final List<PromptSection> sections;

    public SystemPromptComposer(List<PromptSection> sections) {
        this.sections = sections.stream()
                .sorted(Comparator.comparingInt(PromptSection::order))
                .toList();
    }

    public Function<Object, String> asProvider() {
        return memoryId -> compose(memoryId == null ? null : memoryId.toString());
    }

    public String compose(@Nullable String memoryId) {
        StringBuilder sb = new StringBuilder();

        sb.append("当前日期：").append(LocalDate.now().format(DATE_FMT)).append("\n\n");

        for (PromptSection sec : sections) {
            String rendered;
            try {
                rendered = sec.render(memoryId);
            } catch (Exception e) {
                log.error("PromptSection {} render failed, section skipped (memoryId={})",
                        sec.getClass().getSimpleName(), memoryId, e);
                continue;
            }
            if (rendered == null || rendered.isBlank()) continue;
            sb.append(rendered);
            if (!rendered.endsWith("\n")) sb.append("\n");
        }
        return sb.toString();
    }
}
