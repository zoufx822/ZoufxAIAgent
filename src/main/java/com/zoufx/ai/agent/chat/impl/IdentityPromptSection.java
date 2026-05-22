package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.api.HotMemoryType;
import com.zoufx.ai.agent.memory.api.UserImpressionFields;
import com.zoufx.ai.agent.memory.api.UserImpressionFields.FieldSpec;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 「## 关于对方」段（v0.13 取代 v0.12 SystemPromptComposer.appendIdentitySection）。
 *
 * <p>username 是身份锚。本段按 username 是否已知==显式分两路渲染==：
 * <ul>
 *   <li><b>username 已知</b>：按 {@link UserImpressionFields#FIELDS} 顺序遍历所有 hot 有值的字段，
 *       按各自 directive 模板渲染（含 username directive 在内，各字段==平等处理==，无特殊路径）</li>
 *   <li><b>username 缺失</b>：段开头注入"还不认识对方，本轮引一次问称呼"的命令式指令；
 *       其余已识别字段（如 language / role）继续按 directive 渲染——
 *       这部分认识不受 username 缺失影响，照常注入</li>
 * </ul>
 *
 * <p>v0.13 设计沿革：
 * <ul>
 *   <li>display_name 改名 username（不再走 v0.12 的"称呼锚"特殊段）</li>
 *   <li>字段集与 directive 硬编码在 {@link UserImpressionFields}（v0.13 review 后从 yml 改回，
 *       理由见该类 javadoc）</li>
 * </ul>
 *
 * <p>v0.13.1 修正：「username 缺失时请问称呼」从 SOUL principles 抽回本 Section
 * 作为对偶分支。原 v0.13 设计依赖"段是否出现 = 是否已认识"的隐式信号 + SOUL principles 兜底，
 * 实测发现 SOUL principles 里的软语气 + 错误的"chat memory 也空"条件导致 LLM 不问称呼
 * （[[prompt_factual_injection_must_be_imperative]]：软请求语气在 LLM 训练里被视作可拒绝的建议）。
 * v0.13.1 起：段==永远出现==，由段内文本显式表达"已认识 / 还不认识"状态；
 * 问称呼的命令式指令直接落在本 Section 的对偶分支里，符合"SOUL 只描述'我是怎样的人'，
 * Identity 描述'对方是谁 / 该如何对待对方'"的职责分层。
 *
 * <p>v0.13.1 /test-prompt 验证发现（2026-05-22）：TP-05 重复抑制条件未覆盖"用户主动拒绝"；
 * 已在 UNKNOWN_USERNAME_DIRECTIVE 的第 2 条 bullet 扩大抑制条件。TP-08 暴露
 * I1（每轮至少用一次称呼）与 W4（信息密度高于装饰）的冲突——留 v0.14 验证是否自然解决。
 *
 * <p>归属：跨模块协调段（依赖 memory.hot 多个组件），归 chat 编排层。
 *
 * <p>注入顺序：order=20，在 SoulPromptSection (10) 之后、ToolsPromptSection (30) 之前。
 */
@Component
@RequiredArgsConstructor
public class IdentityPromptSection implements PromptSection {

    private static final String USERNAME_KEY = "username";

    /**
     * username 缺失时的命令式指令——按 [[prompt_factual_injection_must_be_imperative]]
     * 模板：陈述事实 + 动词命令 + 反模式枚举。
     *
     * <p>v0.13.1 /test-prompt TP-05 修正：扩充重复抑制条件，将"用户明确拒绝回答"也纳入。
     */
    private static final String UNKNOWN_USERNAME_DIRECTIVE = """
            你还不知道对方的称呼，这是你还不认识的人。
            - 在本轮回复里自然地引一次问称呼（如「我该怎么称呼你？」），与对方主线诉求一同回应，不要单独占一段；
            - 如果 chat memory 显示你之前已经问过称呼且对方没正面答，或对方明确拒绝回答（如说"不用问""没必要"等），==别再追问==——按"你"自然推进即可；
            - 反模式：username 缺失却通篇"你/您"代过、完全不引向认识——是错的，至少引一次。

            """;

    private final HotMemoryStore hotMemoryStore;

    @Override
    public int order() {
        return 20;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId) {
        if (userId == null) return null;
        Map<String, String> snap = hotMemoryStore.snapshot(userId, HotMemoryType.USER_IMPRESSION);

        String username = snap.get(USERNAME_KEY);
        boolean knowsUsername = username != null && !username.isBlank();

        StringBuilder sb = new StringBuilder("## 关于对方\n\n");
        if (!knowsUsername) {
            sb.append(UNKNOWN_USERNAME_DIRECTIVE);
        }
        for (Map.Entry<String, FieldSpec> e : UserImpressionFields.FIELDS.entrySet()) {
            String value = snap.get(e.getKey());
            if (value == null || value.isBlank()) continue;
            String directive = e.getValue().renderDirective();
            sb.append(directive.replace("{}", value));
            if (!directive.endsWith("\n")) sb.append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }
}
