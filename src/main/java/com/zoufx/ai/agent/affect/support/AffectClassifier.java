package com.zoufx.ai.agent.affect.support;

import com.zoufx.ai.agent.affect.model.Affect;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户情绪识别器——一次独立的 flash 调用，只判断"用户这句话的情绪与强度"。
 *
 * <p>与主回复解耦：分类结果只进关系状态/显示层，不写进正文，也不依赖主模型是否愿意打标签。
 * 实测正文内联标签在非思考模式命中率 0~2/5，独立分类器 10/10 且约 0.7s。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AffectClassifier {

    private static final Pattern EMOTION = Pattern.compile("\"emotion\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern INTENSITY = Pattern.compile("\"intensity\"\\s*:\\s*(\\d+)");

    /** 分类指令与用户文本合并为一条 user message——分类器不需要人格/记忆上下文。 */
    private static final String INSTRUCTION = """
            你是情绪识别器。判断用户这句话的情绪与强度，只输出 JSON，不要解释：
            {"emotion":"平静|愉快|好奇|难过|愤怒|困惑","intensity":1-5}

            规则：
            - 愉快：好消息、轻松、期待、被逗乐
            - 好奇：提问、想了解、探索新事物
            - 困惑：没看懂、信息矛盾、不确定
            - 难过：失落、孤独、累、焦虑、想哭、退缩、自我否定、说"不想说了"
            - 愤怒：被冒犯、遭遇不公、被欺负、气愤（即使语气平静）
            - 平静：情绪不明显、纯陈述

            强度：1 很轻，3 明显，5 强烈。混合情绪选更主导的一个。

            用户这句话：
            """;

    @Qualifier("syncChatModel")
    private final ChatModel chatModel;

    /** 识别失败回落「平静/1」——不阻断对话，也不会让表情乱变。 */
    public Affect classify(String userText) {
        try {
            String raw = chatModel.chat(UserMessage.from(INSTRUCTION + userText)).aiMessage().text();
            return parse(raw);
        } catch (Exception e) {
            log.warn("Affect classify failed, fallback to calm: {}", e.toString());
            return Affect.calm();
        }
    }

    static Affect parse(@Nullable String raw) {
        if (raw == null) return Affect.calm();
        Matcher em = EMOTION.matcher(raw);
        if (!em.find()) return Affect.calm();
        int intensity = 1;
        Matcher it = INTENSITY.matcher(raw);
        if (it.find()) {
            try {
                intensity = Integer.parseInt(it.group(1));
            } catch (NumberFormatException ignored) {
                // 保留默认 1
            }
        }
        return Affect.of(em.group(1).trim(), intensity);
    }
}
