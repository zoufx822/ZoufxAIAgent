package com.zoufx.ai.agent.service;

import com.zoufx.ai.agent.assistant.ChatAssistantContract;
import com.zoufx.ai.agent.properties.MoodProperties;
import com.zoufx.ai.agent.properties.RetryProperties;
import com.zoufx.ai.agent.memory.MemoryStoreContract;
import com.zoufx.ai.agent.memory.MemoryStreamContract;
import com.zoufx.ai.agent.model.ChatEvent;
import com.zoufx.ai.agent.util.RetryPolicyHelper;
import com.zoufx.ai.agent.util.WebSearchEventHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天编排服务。一个类里能看见完整的"对话生命周期"：
 *
 * <pre>
 *   chat()
 *     ├── beforeStream(...)          流前 Hook：写 user 到 Memory Stream
 *     └── buildStream(...)           Flux.create 主体
 *           ├── startTokenStream     装配 LC4J TokenStream 6 个回调 → FluxSink
 *           ├── retryWhen            首次 emit 前重试
 *           ├── doOnNext             收集 assistant 文本
 *           ├── onErrorResume        错误兜底成 error event
 *           ├── doOnComplete         触发 onStreamComplete Hook
 *           └── doOnCancel           客户端断开日志
 * </pre>
 *
 * 入参校验由 ChatRequest 上的 Bean Validation 承担（GlobalExceptionHandler 翻译为 HTTP 400）。
 * 重试策略在 {@link #buildRetrySpec} 私有方法中定义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatService {

    @Qualifier("thinkingAssistant")
    private final ChatAssistantContract thinkingAssistant;

    @Qualifier("nonThinkingAssistant")
    private final ChatAssistantContract nonThinkingAssistant;

    private final MemoryStoreContract memoryStore;
    private final MemoryStreamContract memoryStream;
    private final RetryProperties retryProperties;
    private final MoodProperties moodProperties;

    public Flux<ChatEvent> chat(String userId, String prompt, boolean thinking) {
        ChatAssistantContract assistant = thinking ? thinkingAssistant : nonThinkingAssistant;
        AtomicBoolean hasEmitted = new AtomicBoolean(false);
        // 流式 Flux 在单个 subscriber 上 onNext 串行，StringBuilder 线程安全足够；
        // retry 只在首次 emit 前生效，触发时此 buffer 仍是空，不会与重试残留串味
        StringBuilder assistantBuffer = new StringBuilder();

        return beforeStream(userId, prompt)
                .thenMany(buildStream(assistant, userId, prompt, hasEmitted, assistantBuffer));
    }

    /**
     * Hook：流启动前——把 user prompt 写入 Memory Stream（Cold Archive）。
     * 失败仅记日志、返回 {@code Mono.empty()}，不阻断后续 LLM 流——
     * 经历流写失败不应影响主对话（v1 风险表 #10）。
     */
    private Mono<Void> beforeStream(String userId, String prompt) {
        return memoryStream.append(userId, "user", prompt, null)
                .onErrorResume(err -> {
                    log.warn("Failed to append user message to memory_stream [userId={}]: {}",
                            userId, err.toString());
                    return Mono.empty();
                });
    }

    /**
     * LLM 流主体：在最外层 {@code Flux.create} 里启动 LC4J TokenStream，叠加重试 / 收集 / 错误兜底 / 完成钩子。
     */
    private Flux<ChatEvent> buildStream(ChatAssistantContract assistant, String userId, String prompt,
                                        AtomicBoolean hasEmitted, StringBuilder assistantBuffer) {
        return Flux.<ChatEvent>create(sink -> startTokenStream(sink, assistant, userId, prompt, hasEmitted))
                .retryWhen(buildRetrySpec(hasEmitted))
                .doOnNext(event -> {
                    if ("content".equals(event.type())) {
                        assistantBuffer.append(event.data());
                    }
                })
                .onErrorResume(err -> {
                    log.error("Stream error [userId={}]", userId, err);
                    String msg = err.getMessage() != null ? err.getMessage() : "AI 服务异常，请稍后重试";
                    return Flux.just(new ChatEvent("error", msg));
                })
                .doOnComplete(() -> onStreamComplete(userId, assistantBuffer))
                .doOnCancel(() -> log.info("Stream cancelled [userId={}]", userId));
    }

    /**
     * 把 LC4J {@code TokenStream} 的 6 个回调（thinking / content / tool_call / tool_result / error / complete）
     * 装配到 {@code FluxSink}，并触发 {@code .start()} 启动流。
     *
     * {@code hasEmitted} 在任何首次回调里置 true，被 {@link #buildRetrySpec} 据此判断"流是否已开始"。
     * LC4J 的回调跑在框架自己的线程上，与 WebFlux event loop 隔离。
     */
    private void startTokenStream(FluxSink<ChatEvent> sink, ChatAssistantContract assistant,
                                  String userId, String prompt, AtomicBoolean hasEmitted) {
        // v1.1：mood 启用时用 MoodStripper 包装 content 输出——剥离 <!--mood:KEYWORD-->，独立发 mood 事件。
        // 一条请求一个实例：内部维护 tail buffer + 命中状态，请求结束 flush() 兜底。
        final MoodStripper moodStripper = moodProperties.isEnabled()
                ? new MoodStripper(moodProperties.getTailBufferSize(), sink, userId)
                : null;

        assistant.chat(userId, prompt)
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
                    String query = WebSearchEventHelper.extractQuery(evt.request().arguments());
                    log.info("Tool call start [userId={}] {} query={}", userId, name, query);
                    sink.next(new ChatEvent("tool_call", WebSearchEventHelper.toolCallPayload(name, query)));
                })
                .onToolExecuted(exec -> {
                    hasEmitted.set(true);
                    String name = exec.request().name();
                    String result = exec.result();
                    int count = WebSearchEventHelper.countResults(result);
                    log.info("Tool call done [userId={}] {} count={}", userId, name, count);
                    sink.next(new ChatEvent("tool_result", WebSearchEventHelper.toolResultPayload(name, count, result)));
                })
                .onError(sink::error)
                .onCompleteResponse(r -> {
                    log.info("Stream completed [userId={}]", userId);
                    if (moodStripper != null) moodStripper.flush();
                    sink.complete();
                })
                .start();
    }

    /**
     * Hook：流完成后——把拼装好的 assistant 文本写入 Memory Stream。
     * 失败仅记日志，不重抛——经历流写失败不应影响已经返回给用户的对话内容。
     */
    private void onStreamComplete(String userId, StringBuilder buffer) {
        if (buffer.length() == 0) return;
        memoryStream.append(userId, "assistant", buffer.toString(), null)
                .onErrorResume(err -> {
                    log.warn("Failed to append assistant message to memory_stream [userId={}]: {}",
                            userId, err.toString());
                    return Mono.empty();
                })
                .subscribe();
    }

    /**
     * 构造 LLM 调用的重试策略：仅在首次 emit 前对可重试错误生效，按指数退避重试。
     * 调用方需持有 {@code hasEmitted}，并在 LC4J 任一回调触发时置为 true。
     */
    private Retry buildRetrySpec(AtomicBoolean hasEmitted) {
        RetryProperties.Llm cfg = retryProperties.getLlm();
        return Retry.backoff(cfg.getMaxAttempts(), cfg.getMinBackoff())
                .maxBackoff(cfg.getMaxBackoff())
                .filter(err -> !hasEmitted.get() && RetryPolicyHelper.isRetryable(err))
                .doBeforeRetry(rs -> log.warn("LLM retry #{} cause={}",
                        rs.totalRetries() + 1, rs.failure().toString()));
    }

    /**
     * 清空指定 userId 的全部记忆。
     * v1 起 {@link com.zoufx.ai.agent.memory.MemoryStoreContract} 接口本身返回 {@code Mono<Void>}，
     * 阻塞 JDBC + boundedElastic 包装下沉到实现层，调用方按反应式 chain 自然组合。
     */
    public Mono<Void> clearUserMemory(String userId) {
        return memoryStore.deleteByUserId(userId)
                .doOnSubscribe(s -> log.info("Clearing user memory: {}", userId));
    }
}
