package com.zoufx.ai.agent.chat.service;

import com.zoufx.ai.agent.chat.api.ChatAssistant;
import com.zoufx.ai.agent.chat.api.LlmCapabilities;
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
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天编排服务。一个类里能看见完整的"对话生命周期"：
 *
 * <pre>
 *   chat()
 *     ├── beforeStream(...)          流前 Hook：写 user 到 Cold Memory
 *     └── buildStream(...)           Flux.create 主体
 *           ├── startTokenStream     装配 LC4J TokenStream 6 个回调 → FluxSink
 *           ├── retryWhen            首次 emit 前重试
 *           ├── doOnNext             收集 assistant 文本
 *           ├── onErrorResume        错误兜底成 error event
 *           ├── doOnComplete         触发 onStreamComplete Hook（touch + title backfill + 写 assistant）
 *           └── doOnCancel           客户端断开：日志 + fire-and-forget 清理孤儿消息
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
    private final MoodService moodService;
    private final ChatProperties chatProperties;
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
    public Flux<ChatEvent> chat(String anchorId, String prompt, boolean thinking,
                               @Nullable String prevAnchorId, @Nullable String requestUserId) {
        if (thinking && !llmCapabilities.thinkingToggle()) {
            log.warn("Request asks thinking=true but profile [{}] does not support thinkingToggle; ignored",
                    llmCapabilities.profile());
        }

        triggerCompressionIfNeeded(prevAnchorId);

        AtomicBoolean hasEmitted = new AtomicBoolean(false);
        StringBuilder assistantBuffer = new StringBuilder();
        // 瞬时分类情绪（并行先到）+ 正文里 LLM 自行追加的多个情绪，流末合并落库
        AtomicReference<String> instantMood = new AtomicReference<>();
        List<String> inlineMoods = new CopyOnWriteArrayList<>();

        // findUserId 是阻塞 JDBC——包进 boundedElastic 延迟到订阅时执行，绝不在 event loop 上跑。
        // 用 Optional 承载"可能为 null"，避免 Mono.fromCallable 返回 null 退化成空流、与"流本身为空"混淆。
        return Mono.fromCallable(() -> {
                    String found = anchorMemoryStore.findUserId(anchorId);
                    if (found == null && requestUserId != null) {
                        // 前端懒创建：anchor 尚未入库，用请求携带的 userId 按需建立
                        anchorMemoryStore.createSync(anchorId, requestUserId);
                        return Optional.of(requestUserId);
                    }
                    return Optional.ofNullable(found);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(userIdOpt -> {
                    if (userIdOpt.isEmpty()) {
                        log.error("chat: unknown anchorId={}, no userId provided for lazy creation", anchorId);
                        return Flux.just(new ChatEvent("error", "未识别的对话锚点（anchorId 不存在）"));
                    }
                    String userId = userIdOpt.get();
                    return beforeStream(userId, prompt)
                            .thenMany(buildStream(chatAssistant, anchorId, userId, prompt,
                                    hasEmitted, assistantBuffer, instantMood, inlineMoods));
                });
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
     * Hook：流启动前——把 user prompt 写入 Cold Memory（按 userId 分区，跨锚点共享）。
     * 失败仅记日志、不阻断主流。
     */
    private Mono<Void> beforeStream(String userId, String prompt) {
        return coldMemoryStore.append(userId, "user", prompt, null, null)
                .onErrorResume(err -> {
                    log.warn("Failed to append user message to cold_memory [userId={}]: {}",
                            userId, err.toString());
                    return Mono.empty();
                });
    }

    /**
     * LLM 流主体：在最外层 {@code Flux.create} 里启动 LC4J TokenStream，叠加重试 / 收集 / 错误兜底 / 完成钩子。
     */
    private Flux<ChatEvent> buildStream(ChatAssistant assistant, String anchorId, String userId, String prompt,
                                        AtomicBoolean hasEmitted, StringBuilder assistantBuffer,
                                        AtomicReference<String> instantMood, List<String> inlineMoods) {
        // 并行支：先拉本锚点对话窗口（与主流并行、同源），据此快速分类先到的瞬时情绪事件。
        // 整支失败（加载或分类）都吞为 empty 静默不发；重试只裹主流，分类不随之重跑。
        Flux<ChatEvent> instant = chatMemoryStore.loadByAnchorId(anchorId)
                .flatMap(history -> moodService.classify(prompt, history))
                .doOnNext(instantMood::set)
                .map(kw -> new ChatEvent("mood", MoodEventProcessor.moodPayload(kw)))
                .onErrorResume(err -> {
                    log.warn("Instant mood branch failed, skip [anchorId={}]: {}", anchorId, err.toString());
                    return Mono.empty();
                })
                .flux();

        Flux<ChatEvent> main = Flux.<ChatEvent>create(sink ->
                        startTokenStream(sink, assistant, anchorId, userId, prompt, hasEmitted, inlineMoods))
                .retryWhen(buildRetrySpec(hasEmitted));

        return Flux.merge(instant, main)
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
                .doOnComplete(() -> onStreamComplete(anchorId, userId, prompt, assistantBuffer, instantMood.get(), inlineMoods))
                .doOnCancel(() -> {
                    log.info("Stream cancelled [anchorId={}, userId={}]", anchorId, userId);
                    chatMemoryStore.cleanupOrphans(anchorId)
                            .onErrorResume(err -> {
                                log.warn("Post-cancel sanitize failed [anchorId={}]: {}", anchorId, err.toString());
                                return Mono.empty();
                            })
                            .subscribe();
                });
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
                                  List<String> inlineMoods) {
        // 一条请求一个实例：内部维护 tail buffer + 命中状态，请求结束 flush() 兜底。
        final MoodEventProcessor moodStripper = new MoodEventProcessor(32, sink, userId);

        assistant.chat(anchorId, prompt)
                .onPartialThinking(pt -> {
                    hasEmitted.set(true);
                    if (pt != null && pt.text() != null) {
                        sink.next(new ChatEvent("thinking", pt.text()));
                    }
                })
                .onPartialResponse(ct -> {
                    hasEmitted.set(true);
                    moodStripper.accept(ct);
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
                    moodStripper.flush();
                    // moodStripper 在 startTokenStream 局部作用域，必须在此处把正文情绪导出到外部 list
                    // 供 doOnComplete 的 onStreamComplete 持久化到 anchor.last_mood / cold_memory.mood
                    inlineMoods.addAll(moodStripper.getMoods());
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
                                  @Nullable String instantMood, List<String> inlineMoods) {
        // 一轮情绪轨迹 = 瞬时分类（若有，置首）+ 正文里依次追加的情绪。
        // anchor.last_mood 存最后一个（沉淀表情，供头像还原，单词语义）；
        // cold_memory.mood 存整轮逗号拼接（暂塞同一字段，记录情绪轨迹，后续再优化）。
        List<String> moods = new ArrayList<>();
        if (instantMood != null && !instantMood.isBlank()) moods.add(instantMood);
        moods.addAll(inlineMoods);
        String settledMood = moods.isEmpty() ? null : moods.get(moods.size() - 1);
        String moodTrail = moods.isEmpty() ? null : String.join(",", moods);

        anchorMemoryStore.touch(anchorId, settledMood)
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

        if (buffer.length() == 0) {
            // LLM 未产出任何内容（网络中断等），清理 LC4J 已提前写入的孤儿 UserMessage
            chatMemoryStore.removeLastOrphanUserMessage(anchorId)
                    .onErrorResume(err -> {
                        log.warn("Failed to remove orphan user message [anchorId={}]: {}", anchorId, err.toString());
                        return Mono.empty();
                    })
                    .subscribe();
        }

        if (buffer.length() > 0) {
            coldMemoryStore.append(userId, "assistant", buffer.toString(), null, moodTrail)
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
