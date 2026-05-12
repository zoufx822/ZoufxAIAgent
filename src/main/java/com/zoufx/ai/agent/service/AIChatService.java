package com.zoufx.ai.agent.service;

import com.zoufx.ai.agent.assistant.ChatAssistant;
import com.zoufx.ai.agent.config.properties.RetryProperties;
import com.zoufx.ai.agent.memory.MemoryStore;
import com.zoufx.ai.agent.model.ChatEvent;
import com.zoufx.ai.agent.service.adapter.TokenStreamToFluxAdapter;
import com.zoufx.ai.agent.util.RetryPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天编排服务。
 *
 * 职责切分：
 * - LC4J SDK 桥接 / SSE 事件构造 → {@link TokenStreamToFluxAdapter}
 * - 重试与错误兜底 → 本类（v1 会进一步抽出 LlmRetrySpec）
 * - 入参校验 → ChatRequest 上的 Bean Validation（由 GlobalExceptionHandler 兜底）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatService {

    @Qualifier("thinkingAssistant")
    private final ChatAssistant thinkingAssistant;

    @Qualifier("nonThinkingAssistant")
    private final ChatAssistant nonThinkingAssistant;

    private final MemoryStore memoryStore;
    private final RetryProperties retryProperties;
    private final TokenStreamToFluxAdapter tokenStreamAdapter;

    public Flux<ChatEvent> chat(String userId, String prompt, boolean thinking) {
        ChatAssistant assistant = thinking ? thinkingAssistant : nonThinkingAssistant;
        AtomicBoolean hasEmitted = new AtomicBoolean(false);

        return tokenStreamAdapter.bridge(assistant, userId, prompt, hasEmitted)
                .retryWhen(buildRetrySpec(userId, hasEmitted))
                .onErrorResume(err -> {
                    log.error("Stream error [userId={}]", userId, err);
                    String msg = err.getMessage() != null ? err.getMessage() : "AI 服务异常，请稍后重试";
                    return Flux.just(new ChatEvent("error", msg));
                })
                .doOnCancel(() -> log.info("Stream cancelled [userId={}]", userId));
    }

    /**
     * LLM 重试策略：仅在"流尚未推送任何事件"时重试，避免给前端发出半截流后又重来。
     * v1 会抽到独立的 LlmRetrySpec 类。
     */
    private Retry buildRetrySpec(String userId, AtomicBoolean hasEmitted) {
        return Retry.backoff(retryProperties.getLlm().getMaxAttempts(), retryProperties.getLlm().getMinBackoff())
                .maxBackoff(retryProperties.getLlm().getMaxBackoff())
                .filter(err -> !hasEmitted.get() && RetryPolicy.isRetryable(err))
                .doBeforeRetry(rs -> log.warn("LLM retry #{} [userId={}] cause={}",
                        rs.totalRetries() + 1, userId, rs.failure().toString()));
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
