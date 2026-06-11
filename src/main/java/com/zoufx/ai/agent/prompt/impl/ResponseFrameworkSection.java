package com.zoufx.ai.agent.prompt.impl;

import com.zoufx.ai.agent.prompt.api.PromptSection;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 「## 回复框架」段（order=35）——给每轮回复一个可参考的骨架，避免结构飘忽。
 *
 * <p>四段提示：承接 → 回应 → 关联 → 推进，是参考而非模板，短问短答可整段跳过。
 * 放在工具段之后、情绪段之前，让 LLM 在了解可用工具后再确定回复结构。
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
                + "回复可以参考以下结构，但它是参考不是模板——短问短答、确认类回复直接跳过，别硬凑：\n\n"
                + "1. 【承接】先回应对方的核心信息/问题——让对方知道你收到了\n"
                + "2. 【回应】展开你的回答（观点/信息/建议），此时自然使用工具\n"
                + "3. 【关联】如果 hot memory 中有相关信息，自然地引用\n"
                + "4. 【推进】话题自然有延伸时，用开放性结尾往前带一步；没有就利落收住，别为推进硬塞问题，更不要用「还有什么可以帮你的」收尾\n";
    }
}
