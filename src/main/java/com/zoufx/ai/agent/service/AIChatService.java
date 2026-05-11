package com.zoufx.ai.agent.service;

import com.zoufx.ai.agent.assistant.ChatAssistant;
import com.zoufx.ai.agent.config.properties.RetryProperties;
import com.zoufx.ai.agent.memory.MemoryStore;
import com.zoufx.ai.agent.model.ChatEvent;
import com.zoufx.ai.agent.util.RetryPolicy;
import com.zoufx.ai.agent.util.WebSearchEventHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class AIChatService {

    @Autowired
    @Qualifier("thinkingAssistant")
    private ChatAssistant thinkingAssistant;

    @Autowired
    @Qualifier("nonThinkingAssistant")
    private ChatAssistant nonThinkingAssistant;

    @Autowired
    private MemoryStore memoryStore;

    @Autowired
    private RetryProperties retryProperties;

    public Flux<ChatEvent> chat(String userId, String prompt, boolean thinking) {
        if (prompt.isEmpty()) {
            return Flux.just(new ChatEvent("error", "prompt 不能为空"));
        }

        ChatAssistant assistant = thinking ? thinkingAssistant : nonThinkingAssistant;
        AtomicBoolean hasEmitted = new AtomicBoolean(false);

        return Flux.<ChatEvent>create(sink ->
                        assistant.chat(userId, prompt)
                                .onPartialThinking(pt -> {
                                    hasEmitted.set(true);
                                    if (pt != null && pt.text() != null) {
                                        sink.next(new ChatEvent("thinking", pt.text()));
                                    }
                                })
                                .onPartialResponse(ct -> {
                                    hasEmitted.set(true);
                                    sink.next(new ChatEvent("content", ct));
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
                                    sink.complete();
                                })
                                .start()
                )
                .retryWhen(Retry.backoff(retryProperties.getLlm().getMaxAttempts(), retryProperties.getLlm().getMinBackoff())
                        .maxBackoff(retryProperties.getLlm().getMaxBackoff())
                        .filter(err -> !hasEmitted.get() && RetryPolicy.isRetryable(err))
                        .doBeforeRetry(rs -> log.warn("LLM retry #{} [userId={}] cause={}",
                                rs.totalRetries() + 1, userId, rs.failure().toString())))
                .onErrorResume(err -> {
                    log.error("Stream error [userId={}]", userId, err);
                    return Flux.just(new ChatEvent("error", err.getMessage() != null ? err.getMessage() : "AI 服务异常，请稍后重试"));
                })
                .doOnCancel(() -> log.info("Stream cancelled [userId={}]", userId));
    }

    /**
     * 清空指定 userId 的全部记忆。SQLite JDBC 是阻塞调用，包到 boundedElastic 上隔离 event loop。
     */
    public Mono<Void> clearUserMemory(String userId) {
        return Mono.fromRunnable(() -> {
                    log.info("Clearing user memory: {}", userId);
                    memoryStore.deleteByUserId(userId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
