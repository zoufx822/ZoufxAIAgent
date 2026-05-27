package com.zoufx.ai.agent.chat.service;

import com.zoufx.ai.agent.chat.api.ChatAssistant;
import com.zoufx.ai.agent.chat.api.LlmCapabilities;
import com.zoufx.ai.agent.soul.property.SoulProperties;
import com.zoufx.ai.agent.chat.property.ChatProperties;
import com.zoufx.ai.agent.memory.api.AnchorMemoryStore;
import com.zoufx.ai.agent.memory.api.ChatMemoryStore;
import com.zoufx.ai.agent.memory.api.ColdMemoryStore;
import com.zoufx.ai.agent.chat.model.ChatEvent;
import com.zoufx.ai.agent.chat.support.MoodEventProcessor;
import com.zoufx.ai.agent.chat.support.RetryableExceptions;
import com.zoufx.ai.agent.chat.support.WebSearchEvents;
import com.zoufx.ai.agent.tool.api.ToolPrompt;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天编排服务。一个类里能看见完整的"对话生命周期"：
 *
 * <pre>
 *   chat()
 *     ├── beforeStream(...)          流前 Hook：清理孤儿 + 写 user 到 Cold Memory
 *     └── buildStream(...)           Flux.create 主体
 *           ├── startTokenStream     装配 LC4J TokenStream 6 个回调 → FluxSink
 *           ├── retryWhen            首次 emit 前重试
 *           ├── doOnNext             收集 assistant 文本
 *           ├── onErrorResume        错误兜底成 error event
 *           ├── doOnComplete         触发 onStreamComplete Hook（touch + title backfill + 写 assistant）
 *           └── doOnCancel           客户端断开日志
 * </pre>
 *
 * 入参校验由 ChatRequest 上的 Bean Validation 承担（GlobalExceptionHandler 翻译为 HTTP 400）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 自动 backfill title 时取首条用户消息的最大字符数。 */
    private static final int AUTO_TITLE_MAX_LEN = 20;

    private final ChatAssistant chatAssistant;
    private final LlmCapabilities llmCapabilities;
    private final ChatMemoryStore chatMemoryStore;
    private final AnchorMemoryStore anchorMemoryStore;
    private final ColdMemoryStore coldMemoryStore;
    private final AnchorService anchorSummaryService;
    private final ChatProperties chatProperties;
    private final SoulProperties soulProperties;
    private final List<ToolPrompt> tools;
    private final Map<String, String> toolNameMap = new HashMap<>();

    @PostConstruct
    public void initToolNameMap() {
        for (ToolPrompt tool : tools) {
            for (Method m : tool.getClass().getDeclaredMethods()) {
                if (m.isAnnotationPresent(Tool.class)) {
                    toolNameMap.put(m.getName(), tool.section());
                }
            }
        }
    }

    /**
     * anchorId 不存在 fail-fast 成 error event；userId 在入口反查一次供下游共用。
     *
     * <p>{@code thinking} 在 capability.thinkingToggle=false 时被静默忽略 + warn log
     * （LC4J 1.13.1 langchain4j-anthropic 不支持 per-call thinking 覆盖）。
     *
     * <p>{@code prevAnchorId} 非空表示客户端发生锚点切换——fire-and-forget 触发对前一锚点的压缩。
     */
    public Flux<ChatEvent> chat(String anchorId, String prompt, boolean thinking, @Nullable String prevAnchorId) {
        if (thinking && !llmCapabilities.thinkingToggle()) {
            log.warn("Request asks thinking=true but profile [{}] does not support thinkingToggle; ignored",
                    llmCapabilities.profile());
        }

        String userId = anchorMemoryStore.findUserId(anchorId);
        if (userId == null) {
            log.error("chat: unknown anchorId={}", anchorId);
            return Flux.just(new ChatEvent("error", "未识别的对话锚点（anchorId 不存在）"));
        }

        triggerCompressionIfNeeded(prevAnchorId);

        AtomicBoolean hasEmitted = new AtomicBoolean(false);
        // 流式 Flux 在单个 subscriber 上 onNext 串行，StringBuilder 线程安全足够；
        // retry 只在首次 emit 前生效，触发时此 buffer 仍是空，不会与重试残留串味
        StringBuilder assistantBuffer = new StringBuilder();
        // 本轮最后一次 mood，由 MoodEventProcessor.flush() 时回填，onStreamComplete 读取落库
        AtomicReference<String> lastMood = new AtomicReference<>();

        return beforeStream(anchorId, userId, prompt)
                .thenMany(buildStream(chatAssistant, anchorId, userId, prompt, hasEmitted, assistantBuffer, lastMood));
    }

    /**
     * 客户端切换锚点时 fire-and-forget 触发对前一锚点的 LLM 摘要压缩，写入 anchor.summary。
     * 失败不阻断当前主流，由 {@link AnchorService} 内部 catch 兜底。
     */
    private void triggerCompressionIfNeeded(@Nullable String prevAnchorId) {
        if (prevAnchorId == null || prevAnchorId.isBlank()) return;
        log.info("Anchor switch detected, compressing prevAnchorId={}", prevAnchorId);
        anchorSummaryService.compress(prevAnchorId).subscribe();
    }

    /**
     * Hook：流启动前——
     * <ol>
     *   <li>持久化清理 chat_memory 里的孤儿 tool 消息（防止上一次 stop 留下半成品消息序列让 LLM 校验失败）</li>
     *   <li>把 user prompt 写入 Cold Memory（按 userId 分区，跨锚点共享）</li>
     * </ol>
     * 两步都失败仅记日志、不阻断主流——清理/写流失败不应影响主对话。
     */
    private Mono<Void> beforeStream(String anchorId, String userId, String prompt) {
        Mono<Void> cleanup = chatMemoryStore.cleanupOrphans(anchorId)
                .doOnNext(changed -> {
                    if (Boolean.TRUE.equals(changed)) {
                        log.info("Pre-stream sanitize fired [anchorId={}]", anchorId);
                    }
                })
                .onErrorResume(err -> {
                    log.warn("Pre-stream sanitize failed [anchorId={}]: {}", anchorId, err.toString());
                    return Mono.empty();
                })
                .then();
        Mono<Void> appendUser = coldMemoryStore.append(userId, "user", prompt, null, null)
                .onErrorResume(err -> {
                    log.warn("Failed to append user message to cold_memory [userId={}]: {}",
                            userId, err.toString());
                    return Mono.empty();
                });
        return cleanup.then(appendUser);
    }

    /**
     * LLM 流主体：在最外层 {@code Flux.create} 里启动 LC4J TokenStream，叠加重试 / 收集 / 错误兜底 / 完成钩子。
     */
    private Flux<ChatEvent> buildStream(ChatAssistant assistant, String anchorId, String userId, String prompt,
                                        AtomicBoolean hasEmitted, StringBuilder assistantBuffer,
                                        AtomicReference<String> lastMood) {
        return Flux.<ChatEvent>create(sink -> startTokenStream(sink, assistant, anchorId, userId, prompt, hasEmitted, lastMood))
                .retryWhen(buildRetrySpec(hasEmitted))
                .doOnNext(event -> {
                    if ("content".equals(event.type())) {
                        assistantBuffer.append(event.data());
                    }
                })
                .onErrorResume(err -> {
                    log.error("Stream error [anchorId={}, userId={}]", anchorId, userId, err);
                    String msg = err.getMessage() != null ? err.getMessage() : "AI 服务异常，请稍后重试";
                    return Flux.just(new ChatEvent("error", msg));
                })
                .doOnComplete(() -> onStreamComplete(anchorId, userId, prompt, assistantBuffer, lastMood.get()))
                .doOnCancel(() -> log.info("Stream cancelled [anchorId={}, userId={}]", anchorId, userId));
    }

    /**
     * 把 LC4J {@code TokenStream} 的 6 个回调（thinking / content / tool_call / tool_result / error / complete）
     * 装配到 {@code FluxSink}，并触发 {@code .start()} 启动流。
     *
     * {@code hasEmitted} 在任何首次回调里置 true，被 {@link #buildRetrySpec} 据此判断"流是否已开始"。
     * LC4J 的回调跑在框架自己的线程上，与 WebFlux event loop 隔离。
     */
    private void startTokenStream(FluxSink<ChatEvent> sink, ChatAssistant assistant,
                                  String anchorId, String userId, String prompt, AtomicBoolean hasEmitted,
                                  AtomicReference<String> lastMood) {
        // mood 启用时用 MoodEventProcessor 包装 content 输出——剥离 <!--mood:KEYWORD-->，独立发 mood 事件。
        // 一条请求一个实例：内部维护 tail buffer + 命中状态，请求结束 flush() 兜底。
        final MoodEventProcessor moodStripper = soulProperties.getMood().isEnabled()
                ? new MoodEventProcessor(soulProperties.getMood().getTailBufferSize(), sink, userId)
                : null;

        assistant.chat(anchorId, prompt)
                .onPartialThinking(pt -> {
                    hasEmitted.set(true);
                    if (pt != null && pt.text() != null) {
                        sink.next(new ChatEvent("thinking", pt.text()));
                    }
                })
                .onPartialResponse(ct -> {
                    hasEmitted.set(true);
                    if (moodStripper != null) {
                        moodStripper.accept(ct);
                    } else {
                        sink.next(new ChatEvent("content", ct));
                    }
                })
                .beforeToolExecution(evt -> {
                    hasEmitted.set(true);
                    String name = evt.request().name();
                    String query = WebSearchEvents.extractQuery(evt.request().arguments());
                    String chineseName = toolNameMap.getOrDefault(name, name);
                    log.info("Tool call start [anchorId={}] {} ({}) query={}", anchorId, name, chineseName, query);
                    sink.next(new ChatEvent("tool_call", WebSearchEvents.toolCallPayload(name, chineseName, query)));
                })
                .onToolExecuted(exec -> {
                    hasEmitted.set(true);
                    String name = exec.request().name();
                    String result = exec.result();
                    String chineseName = toolNameMap.getOrDefault(name, name);
                    int count = WebSearchEvents.countResults(result);
                    log.info("Tool call done [anchorId={}] {} ({}) count={}", anchorId, name, chineseName, count);
                    sink.next(new ChatEvent("tool_result", WebSearchEvents.toolResultPayload(name, chineseName, count, result)));
                })
                .onError(sink::error)
                .onCompleteResponse(r -> {
                    log.info("Stream completed [anchorId={}]", anchorId);
                    if (moodStripper != null) {
                        moodStripper.flush();
                        // moodStripper 在 startTokenStream 局部作用域，必须在此处把 mood 落到外部 ref
                        // 供 doOnComplete 的 onStreamComplete 持久化到 anchor.last_mood / cold_memory.mood
                        lastMood.set(moodStripper.getLastMood());
                    }
                    sink.complete();
                })
                .start();
    }

    /**
     * Hook：流完成后——
     * <ol>
     *   <li>touch 锚点（last_active_at = now，summary 置 NULL）</li>
     *   <li>title 为空时用首条用户消息截取自动 backfill</li>
     *   <li>把拼装好的 assistant 文本写入 Cold Memory</li>
     * </ol>
     * 全部异步、失败仅记日志，不重抛——副作用失败不应影响已经返回给用户的对话内容。
     */
    private void onStreamComplete(String anchorId, String userId, String prompt, StringBuilder buffer,
                                  @Nullable String lastMood) {
        anchorMemoryStore.touch(anchorId, lastMood)
                .onErrorResume(err -> {
                    log.warn("Failed to touch anchor [anchorId={}]: {}", anchorId, err.toString());
                    return Mono.empty();
                })
                .subscribe();

        String autoTitle = truncate(prompt, AUTO_TITLE_MAX_LEN);
        if (!autoTitle.isBlank()) {
            anchorMemoryStore.updateTitleIfBlank(anchorId, autoTitle)
                    .onErrorResume(err -> {
                        log.warn("Failed to backfill anchor title [anchorId={}]: {}", anchorId, err.toString());
                        return Mono.empty();
                    })
                    .subscribe();
        }

        if (buffer.length() > 0) {
            coldMemoryStore.append(userId, "assistant", buffer.toString(), null, lastMood)
                    .onErrorResume(err -> {
                        log.warn("Failed to append assistant message to cold_memory [userId={}]: {}",
                                userId, err.toString());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        String trimmed = s.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /**
     * 构造 LLM 调用的重试策略：仅在首次 emit 前对可重试错误生效，按指数退避重试。
     * 调用方需持有 {@code hasEmitted}，并在 LC4J 任一回调触发时置为 true。
     */
    private Retry buildRetrySpec(AtomicBoolean hasEmitted) {
        ChatProperties.Retry r = chatProperties.getRetry();
        return Retry.backoff(r.getMaxAttempts(), r.getMinBackoff())
                .maxBackoff(r.getMaxBackoff())
                .filter(err -> !hasEmitted.get() && RetryableExceptions.isRetryable(err))
                .doBeforeRetry(rs -> log.warn("LLM retry #{} cause={}",
                        rs.totalRetries() + 1, rs.failure().toString()));
    }

}
