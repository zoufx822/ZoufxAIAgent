package com.zoufx.ai.agent.chat.impl;

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
 * System prompt 编排器（v0.13 重构：从过程式拼接 → 编排器 + PromptSection）。
 *
 * <h2>v0.13 改造对比 v0.12</h2>
 * <pre>
 *   v0.12 (260 行)：appendSoulSection / appendIdentitySection / renderTools / appendMoodSection
 *                   全部为本类的私有方法；硬编码 ROLE / STRANGER_GREETING / GLOBAL_RULES / KEY_TEMPLATES
 *   v0.13 (~40 行)：本类退化为编排器，构造注入 List<PromptSection>，按 order 升序串行 render
 *                   顶部仅"当前日期"一行无条件出现，不走 Section
 *                   业务逻辑全部内聚到各 Section（SoulPromptSection / IdentityPromptSection / ...）
 * </pre>
 *
 * <h2>v0.13 review 改进：role 并入 SoulPromptSection</h2>
 * 初版让 role 在本类顶部独立渲染（"你是 {role}。"提到第一行），代价是本类多依赖 SoulStore、
 * seed 与 enabled-keys 不对称需 javadoc 额外说明——review 后把 role 也按 enabled-key 处理，
 * 渲染在 {@code SoulPromptSection} 的「## 关于你自己」段开头。本类退化到==只管"日期 + Section 编排"==。
 *
 * <h2>Frozen Snapshot 约束（v0.1 起显式化，v0.13 保留）</h2>
 * {@link #compose(String)} 由 LC4J 作为 SystemMessageProvider 在 ==每次 chat 请求的开始处==
 * 同步内联调用 ==一次==——单请求 system prompt 自然冻结。
 *
 * <p>==请勿==在响应流的任何阶段（doOnNext / 工具回调 / 流尾 hook 等）主动重新调用 compose()，
 * 否则会破坏「Hot Memory 修改要到下次请求才生效」的 Hermes Frozen Snapshot 语义。
 *
 * <h2>线程上下文</h2>
 * 本方法被 LC4J 作为 SystemMessageProvider 同步契约调用，在调用方线程上内联执行。
 * 上层从 WebFlux event loop 触发 chat，故 compose 也在 event loop 上跑——
 * ==所有 Store 读取必须用同步签名==，PromptSection 实现禁止 .block() 反应式 Mono。
 */
@Slf4j
@Component
public class SystemPromptComposer {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA);

    private final List<PromptSection> sections;

    public SystemPromptComposer(List<PromptSection> sections) {
        // 排序一次性发生在 Bean 初始化时——每次 compose() 不重新排序
        this.sections = sections.stream()
                .sorted(Comparator.comparingInt(PromptSection::order))
                .toList();
    }

    public Function<Object, String> asProvider() {
        return memoryId -> compose(memoryId == null ? null : memoryId.toString());
    }

    public String compose(@Nullable String memoryId) {
        StringBuilder sb = new StringBuilder();

        // 顶部唯一一行：当前日期（无条件出现，不走 PromptSection）
        // 每请求动态计算；让 LLM 看顶部即可，工具 prompt 不再做 {today} 替换
        sb.append("当前日期：").append(LocalDate.now().format(DATE_FMT)).append("\n\n");

        for (PromptSection sec : sections) {
            String rendered;
            try {
                rendered = sec.render(memoryId);
            } catch (Exception e) {
                // 降级：单个 Section 失败不连累整段 prompt 组装。失败的 Section 跳过，
                // 其余正常注入；LLM 收到的 prompt 缺这一段但能继续工作（最坏退化为"忘了某条信息"）
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
