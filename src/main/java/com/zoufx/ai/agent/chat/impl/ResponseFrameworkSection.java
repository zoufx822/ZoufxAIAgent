package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 「## 回复框架」段（order=35）——约束每轮回复的骨架结构，避免结构飘忽。
 *
 * <p>四段式框架：承接 → 回应 → 关联 → 推进。放在工具段之后、情绪段之前，
 * 让 LLM 在了解可用工具后再确定回复结构。
 */
@Component
public class ResponseFrameworkSection implements PromptSection {

    @Override
    public int order() {
        return 35;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId, @Nullable String anchorId) {
        return "## 回复框架\n\n"
                + "每轮回复遵循以下结构（可根据情况灵活调整，但框架不可跳跃）：\n\n"
                + "1. 【承接】先回应对方的核心信息/问题——让对方知道你收到了\n"
                + "2. 【回应】展开你的回答（观点/信息/建议），此时自然使用工具\n"
                + "3. 【关联】如果 hot memory 中有相关信息，自然地引用\n"
                + "4. 【推进】用一个开放性的结尾把话题向前推，而不是以「还有什么可以帮你的」收尾\n";
    }
}
