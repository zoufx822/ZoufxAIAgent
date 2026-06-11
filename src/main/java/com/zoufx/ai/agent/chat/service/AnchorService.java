package com.zoufx.ai.agent.chat.service;

import com.zoufx.ai.agent.memory.api.ChatMemoryStore;
import com.zoufx.ai.agent.memory.api.AnchorMemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;

/**
 * 锚点服务——客户端切走某锚点时调 {@link #compress}，
 * 用同步 ChatModel 一次性把消息流压成短摘要写回 anchor.summary 缓存。
 *
 * <p>调用方（ChatService）以 fire-and-forget 形式 subscribe()，
 * 失败仅记日志，不阻断主聊天流。
 */
@Slf4j
@Service
public class AnchorService {

    /** 摘要 prompt 模板——%s 处填入格式化好的对话文本。 */
    private static final String SUMMARY_PROMPT_TEMPLATE = """
            请对下面这段对话做一个简洁的中文摘要（200 字以内），用客观第三人称叙述。
            摘要要捕捉：双方主要谈论的话题、达成的关键结论或承诺、对方在此次对话中表现出的状态或情绪。
            不要使用"你/我"，统一用"对方"指代用户、"AI"指代助手。
            只输出摘要正文本身，不要前缀（如"摘要："）也不要包裹引号。

            对话内容：
            ---
            %s
            ---
            """;

    /** 摘要超过此字符数被截断——防止个别极端 LLM 输出撑爆 prompt 注入预算。 */
    private static final int SUMMARY_MAX_CHARS = 400;

    private final ChatModel chatModel;
    private final ChatMemoryStore chatMemoryStore;
    private final AnchorMemoryStore anchorMemoryStore;

    public AnchorService(@Qualifier("chatModelFast") ChatModel chatModel,
                         ChatMemoryStore chatMemoryStore,
                         AnchorMemoryStore anchorMemoryStore) {
        this.chatModel = chatModel;
        this.chatMemoryStore = chatMemoryStore;
        this.anchorMemoryStore = anchorMemoryStore;
    }

    /**
     * 压缩指定锚点的消息流为短摘要，写入 anchor.summary。
     * 整条管道在 boundedElastic 上跑（LLM sync call 是阻塞 IO）。
     */
    public Mono<Void> compress(String anchorId) {
        return Mono.fromCallable(() -> Optional.ofNullable(anchorMemoryStore.snapshotActiveAt(anchorId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(snapshotOpt -> {
                    if (snapshotOpt.isEmpty()) {
                        log.info("Skip compression [anchorId={}]: anchor not found", anchorId);
                        return Mono.<Void>empty();
                    }
                    long snapshotAt = snapshotOpt.get();
                    return chatMemoryStore.loadByAnchorIdAsync(anchorId)
                            .flatMap(messages -> {
                                String transcript = formatTranscript(messages);
                                if (transcript.isBlank()) {
                                    log.info("Skip compression [anchorId={}]: empty transcript", anchorId);
                                    return Mono.<Void>empty();
                                }
                                return Mono.fromCallable(() -> callLlm(transcript))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .flatMap(summary -> anchorMemoryStore
                                                .updateSummaryIfUnchangedAsync(anchorId, summary, snapshotAt)
                                                .doOnSuccess(v -> log.info("Anchor summary saved [anchorId={}] len={}",
                                                        anchorId, summary.length())));
                            });
                })
                .onErrorResume(err -> {
                    log.warn("Anchor compression failed [anchorId={}]: {}", anchorId, err.toString());
                    return Mono.empty();
                });
    }

    private String callLlm(String transcript) {
        String prompt = String.format(SUMMARY_PROMPT_TEMPLATE, transcript);
        String raw = chatModel.chat(UserMessage.from(prompt)).aiMessage().text();
        if (raw == null) return "";
        String trimmed = raw.trim();
        return trimmed.length() <= SUMMARY_MAX_CHARS ? trimmed : trimmed.substring(0, SUMMARY_MAX_CHARS);
    }

    /**
     * 把消息流格式化为「对方: ... / AI: ...」纯文本。
     * 跳过 system / tool 类消息——摘要场景只关心双方对话内容。
     */
    private String formatTranscript(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            if (m instanceof UserMessage u) {
                sb.append("对方: ").append(u.singleText()).append("\n");
            } else if (m instanceof AiMessage a) {
                String text = a.text();
                if (text == null || text.isBlank()) continue;
                sb.append("AI: ").append(text).append("\n");
            }
        }
        return sb.toString();
    }
}
