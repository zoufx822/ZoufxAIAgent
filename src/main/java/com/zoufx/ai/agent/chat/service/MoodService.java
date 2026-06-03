package com.zoufx.ai.agent.chat.service;

import com.zoufx.ai.agent.chat.support.Moods;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 情绪服务——用户消息一到就并行起一次无上下文的轻量同步 LLM 调用快速分类，
 * 抢在主推理流之前判出小Z 的"第一反应情绪"，让前端头像立刻变脸（读脸般的瞬时反应）。
 *
 * <p>只看当轮用户输入（不带记忆/历史）以换取最低延迟。复用 {@code chatModelFast}（flash 轻量同步），
 * 整条调用包在 boundedElastic 上，绝不碰 event loop。
 *
 * <p>失败语义有别：模型有响应但输出杂乱 → {@link Moods#normalize} 回落「平静」；
 * 真异常/超时 → 返回 {@code Mono.empty()} 不发情绪事件，避免给出一张错误的"平静脸"。
 */
@Slf4j
@Service
public class MoodService {

    private static final String PROMPT_TEMPLATE = """
            你是「小Z」。下面是对方刚发来的一句话。
            判断你此刻读到它的第一反应情绪，从下面 6 个词里精确选 1 个，
            只输出这个词本身，不要解释、不要标点、不要任何多余字符：
            平静 / 兴奋 / 难过 / 愤怒 / 好奇 / 困惑

            判断要点：
            - 对方倾诉负面经历 / 低落挫败 → 难过（与对方共情）
            - 对方分享好消息 / 取得进展 → 兴奋
            - 对方遭遇明显不公 / 被欺负伤害 → 愤怒（为对方义愤）
            - 对方抛出新颖问题 / 陌生领域 → 好奇
            - 对方表达模糊 / 信息冲突 / 要求再解释 → 困惑
            - 日常闲聊 / 信息陈述 / 拿不准 → 平静

            对方的话：
            ---
            %s
            ---
            """;

    private final ChatModel chatModel;

    public MoodService(@Qualifier("chatModelFast") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** 返回 6 词之一；异常/超时吞掉为 empty，不影响主流。 */
    public Mono<String> classify(String userMessage) {
        return Mono.fromCallable(() -> {
                    String raw = chatModel.chat(UserMessage.from(String.format(PROMPT_TEMPLATE, userMessage)))
                            .aiMessage().text();
                    String mood = Moods.normalize(raw);
                    log.info("Mood classified instant={} (raw={})", mood, raw == null ? "" : raw.trim());
                    return mood;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(err -> {
                    log.warn("Mood classification failed, skip instant mood: {}", err.toString());
                    return Mono.empty();
                });
    }
}
