package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.soul.property.MoodProperties;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「## 情绪标记」段（v0.13 从 SystemPromptComposer.appendMoodSection 抽出）。
 *
 * <p>命令 LLM 在每条回复末尾追加 {@code <!--mood:KEYWORD-->} HTML 注释。
 * 后端 ChatService 在 content 流尾部用 tail buffer 扫描剥离，独立成 SSE mood 事件。
 *
 * <p>用命令式 + 反模式枚举，避免 weak model 漏标。
 *
 * <p>注入顺序：order=40，作为 prompt 末段。
 */
@Component
@RequiredArgsConstructor
public class MoodPromptSection implements PromptSection {

    private final MoodProperties moodProperties;

    @Override
    public int order() {
        return 40;
    }

    @Override
    @Nullable
    public String render(@Nullable String memoryId) {
        if (!moodProperties.isEnabled()) return null;
        List<String> keywords = moodProperties.getKeywords();
        if (keywords == null || keywords.isEmpty()) return null;

        return "## 情绪标记\n\n"
                + "在你每条回复的**最末尾**，追加一个 HTML 注释标记你此刻的情绪，格式严格如下：\n"
                + "<!--mood:KEYWORD-->\n\n"
                + "KEYWORD 必须从以下词表中精确选择一个：\n"
                + String.join(" / ", keywords) + "\n\n"
                + "规则：\n"
                + "- 该注释不会显示给用户，仅供系统读取\n"
                + "- 务必每条回复都追加，不可省略\n"
                + "- 必须放在回复的最末尾（在所有正文与标点之后）\n"
                + "- 反模式：\n"
                + "  - 不要使用词表以外的词\n"
                + "  - 不要在注释里加解释（如 <!--mood:好奇，因为这个问题很有趣-->）\n"
                + "  - 不要把标记夹在正文中间\n";
    }
}
