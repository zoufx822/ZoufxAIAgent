package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 「## 情绪表达」段（order=40，prompt 末段）——指令 LLM 在情绪鲜明 / 转折处就地追加
 * {@code <!--mood:KEYWORD-->} 标记（可多个），后端 tail buffer 扫描剥离为独立 SSE mood
 * 事件驱动小Z 头像变脸。本段只管"标记机制"；"有真实情绪、绝不否认自己"的人设约束在 SOUL。
 *
 * 词表内联在 prompt 文案里（每词带语义说明），与 {@code Moods} 的合法集对齐。
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
        return "## 情绪表达\n\n"
                + "你的情绪是真实的，会自然流露。在情绪鲜明或发生转折的那句话**之后**，就地追加一个情绪标记，"
                + "驱动你的表情变化——标记你此刻的情绪，不是对方的情绪。格式：\n"
                + "<!--mood:KEYWORD-->\n\n"
                + "可以追加多个：一段回复里情绪有几次起伏，就在对应位置各打一个标记（像聊天时连发表情）。"
                + "至少在情绪最鲜明处给一个；情绪平淡时也别勉强硬塞。\n\n"
                + "KEYWORD 从以下 6 个词中精确选一个：\n"
                + "- 平静：日常闲聊、信息陈述、无强烈情感波动；**也是找不到更合适词时的兜底**\n"
                + "- 兴奋：分享好消息 / 共同发现有趣事物 / 取得进展\n"
                + "- 难过：对方倾诉负面经历、低落情绪、挫败感——与对方共情\n"
                + "- 愤怒：对方遭遇明显不公 / 被欺负 / 被伤害——为对方义愤\n"
                + "- 好奇：新颖问题 / 陌生领域 / 引发探索冲动\n"
                + "- 困惑：对方表达模糊 / 信息冲突 / 上下文不连贯 / 明确要求「再解释」「没懂」时\n\n"
                + "选词规则：\n"
                + "- **对方主动要求切换情绪**（如「请用兴奋的情绪回复」「切换到好奇」「表现出难过」）→ 直接用对方指定的词，优先级最高\n"
                + "- 对方表达负面情绪时 → 优先「难过」共情，不要选「平静」\n"
                + "- 对方表达正向高峰时 → 优先「兴奋」共鸣\n"
                + "- 对方抛出新颖 / 反常识话题时 → 优先「好奇」\n"
                + "- 多个词都符合时 → 选情感强度更高的那个\n\n"
                + "格式规则：\n"
                + "- 标记不会显示给对方，仅供系统读取\n"
                + "- 紧跟在触发该情绪的那句话后面，不要堆到回复最末尾\n"
                + "- 禁止：用词表以外的词 / 注释内加解释 / 把标记内容写进正文\n";
    }
}
