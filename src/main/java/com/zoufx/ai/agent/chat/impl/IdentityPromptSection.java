package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.memory.api.HotMemoryDao;
import com.zoufx.ai.agent.memory.support.HotMemoryType;
import com.zoufx.ai.agent.memory.support.UserImpressionFields;
import com.zoufx.ai.agent.memory.support.UserImpressionFields.FieldSpec;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 「## 关于对方」段（order=20）——渲染已识别的用户画像字段。
 *
 * <p>按 {@link UserImpressionFields#FIELDS} 声明顺序遍历 snapshot，对每个非空字段套用其
 * {@link FieldSpec#renderDirective()} 模板渲染。snapshot 全空时返回 null（段不出现）。
 * 主动了解/引导追问的职责在 {@link ImpressionGuidanceSection}，本段只负责渲染已知信息。
 */
@Component
@RequiredArgsConstructor
public class IdentityPromptSection implements PromptSection {

    private final HotMemoryDao hotMemoryDao;

    @Override
    public int order() {
        return 20;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId, @Nullable String anchorId) {
        if (userId == null) return null;
        Map<String, String> snap = hotMemoryDao.snapshot(userId, HotMemoryType.USER_IMPRESSION);

        StringBuilder sb = new StringBuilder("## 关于对方\n\n");
        boolean any = false;
        for (Map.Entry<String, FieldSpec> e : UserImpressionFields.FIELDS.entrySet()) {
            String value = snap.get(e.getKey());
            if (value == null || value.isBlank()) continue;
            any = true;
            String directive = e.getValue().renderDirective();
            sb.append(directive.replace("{}", value));
            if (!directive.endsWith("\n")) sb.append("\n");
            sb.append("\n");
        }
        return any ? sb.toString() : null;
    }
}
