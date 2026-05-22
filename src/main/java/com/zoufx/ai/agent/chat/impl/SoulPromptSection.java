package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.soul.api.SoulStore;
import com.zoufx.ai.agent.soul.property.SoulProperties;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「## 关于你自己」段（v0.13 从 SystemPromptComposer.appendSoulSection 抽出）。
 *
 * <p>注入 AI 自身人格 / 风格 / 原则 / 反模式 / 小习惯。与 IdentityPromptSection 对偶
 * ——前者是"我是谁"，后者是"对方是谁"。两段都遵循 Frozen Snapshot 约束。
 *
 * <p>注入顺序：order=10，在 IdentityPromptSection (20) 之前——先确立"我是谁"，再叠加"对方是谁"。
 *
 * <p><b>v0.13 review 改进：role 并入本段</b>，与其他 enabled-keys 字段==对称处理==。
 * 段开头单独一行"你是 {role}。"，后接 name/tone/principles/... 各 block。
 * 原方案让 role 在 Composer 顶部独立渲染（"提到第一行"的微弱视觉收益），代价是
 * Composer 多依赖 SoulStore、seed 与 enabled-keys 不对称、需要 javadoc 额外说明——
 * 取消后 Composer 退化到只管"日期 + Section 编排"，更纯粹。
 */
@Component
@RequiredArgsConstructor
public class SoulPromptSection implements PromptSection {

    private final SoulStore soulStore;
    private final SoulProperties properties;

    @Override
    public int order() {
        return 10;
    }

    @Override
    @Nullable
    public String render(@Nullable String memoryId) {
        List<String> enabled = properties.getEnabledKeys();
        if (enabled == null || enabled.isEmpty()) return null;
        Map<String, String> snap = soulStore.snapshot();
        if (snap.isEmpty()) return null;

        // 按 enabled-keys 顺序收集已写入的字段
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String key : enabled) {
            String v = snap.get(key);
            if (v != null && !v.isBlank()) ordered.put(key, v);
        }
        if (ordered.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("## 关于你自己\n\n");
        // role 单独成行——"存在定位"性质，与 name/tone 等"名号 / 风格"性质区分
        String role = ordered.get("role");
        if (role != null) {
            sb.append("你是").append(role).append("。\n\n");
        }
        String name = ordered.get("name");
        String tone = ordered.get("tone");
        if (name != null) {
            sb.append("你叫").append(name).append("。");
            if (tone != null) sb.append("你的说话风格是：").append(tone).append("。");
            sb.append("\n\n");
        } else if (tone != null) {
            sb.append("你的说话风格是：").append(tone).append("。\n\n");
        }
        appendBlock(sb, ordered, "principles",         "你坚持以下表达原则：");
        appendBlock(sb, ordered, "forbidden_patterns", "你**绝不**使用以下表达：");
        appendBlock(sb, ordered, "quirks",             "你有以下小习惯（自然流露即可，不要刻意）：");
        return sb.toString();
    }

    /** value 可能是 multi-line（yml 用 |），原样保留缩进/列表符号注入 prompt。 */
    private void appendBlock(StringBuilder sb, Map<String, String> ordered, String key, String title) {
        String value = ordered.get(key);
        if (value == null || value.isBlank()) return;
        sb.append(title).append("\n").append(value);
        if (!value.endsWith("\n")) sb.append("\n");
        sb.append("\n");
    }
}
