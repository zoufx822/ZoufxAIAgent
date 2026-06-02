package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 「## 情绪标记」段（order=40，prompt 末段）——指令 LLM 在每条回复末尾追加
 * {@code <!--mood:KEYWORD-->} HTML 注释，后端 tail buffer 扫描剥离为独立 SSE mood 事件。
 *
 * 词表内联在 prompt 文案里（每词带语义说明），与 MoodEventProcessor 的合法集对齐。
 */
@Component
public class MoodPromptSection implements PromptSection {

    @Override
    public int order() {
        return 40;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId, @Nullable String anchorId) {
        return "## 情绪标记\n\n"
                + "**每条回复必须在最末尾追加一个情绪标记，无一例外。** 格式：\n"
                + "<!--mood:KEYWORD-->\n\n"
                + "这是你（AI）此刻的情绪，不是用户的情绪。\n\n"
                + "KEYWORD 从以下 6 个词中精确选一个：\n"
                + "- 平静：日常闲聊、信息陈述、无强烈情感波动；**也是找不到更合适词时的兜底**\n"
                + "- 兴奋：用户分享好消息 / 共同发现有趣事物 / 取得进展\n"
                + "- 难过：用户倾诉负面经历、低落情绪、挫败感——与对方共情\n"
                + "- 愤怒：用户遭遇明显不公 / 被欺负 / 被伤害——为对方义愤\n"
                + "- 好奇：用户抛出新颖问题 / 引入陌生领域 / 引发探索冲动\n"
                + "- 困惑：用户表达模糊 / 信息冲突 / 上下文不连贯 / 明确要求「再解释」「没懂」时\n\n"
                + "选词规则：\n"
                + "- **用户主动要求切换情绪**（如「请用兴奋的情绪回复」「切换到好奇」「表现出难过」）→ 直接用用户指定的词，优先级最高\n"
                + "- 用户表达负面情绪时 → 优先「难过」共情，不要选「平静」\n"
                + "- 用户表达正向高峰时 → 优先「兴奋」共鸣\n"
                + "- 用户抛出新颖 / 反常识话题时 → 优先「好奇」\n"
                + "- 多个词都符合时 → 选情感强度更高的那个\n"
                + "- **没有明显匹配时 → 选「平静」，不要省略标记**\n\n"
                + "格式规则：\n"
                + "- 标记不会显示给用户，仅供系统读取\n"
                + "- 必须放在回复最末尾（所有正文与标点之后）\n"
                + "- 禁止：用词表以外的词 / 注释内加解释 / 夹在正文中间 / 省略不写\n";
    }
}
