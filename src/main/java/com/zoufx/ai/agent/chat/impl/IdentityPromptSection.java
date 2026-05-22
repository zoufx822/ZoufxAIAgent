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
 * <p>遍历 {@link UserImpressionFields#FIELDS} 中已配置的所有字段（如 username/language/role/...），
 * 对 hot_memory 中有值的字段按其 directive 模板渲染。各字段==平等渲染==，无特殊路径。
 *
 * <p>v0.13 设计调整：
 * <ul>
 *   <li>display_name 改名 username，与其他字段对称处理（不再走 v0.12 的"称呼锚"特殊段）</li>
 *   <li>"如何对待陌生/已认识的人"作为通用人际行为原则并入 SOUL principles，永久注入</li>
 *   <li>"段是否出现" = "是否已认识"的隐式信号，LLM 通过 chat memory 自感知初识</li>
 *   <li>没有"三态判定分支"，状态机藏在「段是否出现 + SOUL 原则」里</li>
 *   <li>字段集与 directive 硬编码在 {@link UserImpressionFields}（v0.13 review 后从 yml 改回，
 *       理由见该类 javadoc）</li>
 * </ul>
 *
 * <p>归属：跨模块协调段（依赖 memory.hot 多个组件），归 chat 编排层。
 *
 * <p>注入顺序：order=20，在 SoulPromptSection (10) 之后、ToolsPromptSection (30) 之前。
 */
@Component
@RequiredArgsConstructor
public class IdentityPromptSection implements PromptSection {

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
        if (snap.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("## 关于对方\n\n");
        boolean any = false;
        for (Map.Entry<String, FieldSpec> e : UserImpressionFields.FIELDS.entrySet()) {
            String value = snap.get(e.getKey());
            if (value == null || value.isBlank()) continue;
            String directive = e.getValue().renderDirective();
            sb.append(directive.replace("{}", value));
            if (!directive.endsWith("\n")) sb.append("\n");
            sb.append("\n");
            any = true;
        }
        return any ? sb.toString() : null;
    }
}
