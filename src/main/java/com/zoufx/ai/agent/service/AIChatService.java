package com.zoufx.ai.agent.service;

import com.zoufx.ai.agent.assistant.ChatAssistant;
import com.zoufx.ai.agent.config.RetryProperties;
import com.zoufx.ai.agent.model.ChatEvent;
import com.zoufx.ai.agent.util.RetryPolicy;
import com.zoufx.ai.agent.util.WebSearchEventHelper;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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
    private ChatMemoryStore chatMemoryStore;

    @Autowired
    private RetryProperties retryProperties;

    public Flux<ChatEvent> chat(String sessionId, String prompt, boolean thinking) {
        if (prompt.isEmpty()) {
            return Flux.just(new ChatEvent("error", "prompt 不能为空"));
        }

        ChatAssistant assistant = thinking ? thinkingAssistant : nonThinkingAssistant;
        AtomicBoolean hasEmitted = new AtomicBoolean(false);

        return Flux.<ChatEvent>create(sink ->
                        assistant.chat(sessionId, prompt)
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
                                    log.info("Tool call start [sessionId={}] {} query={}", sessionId, name, query);
                                    sink.next(new ChatEvent("tool_call", WebSearchEventHelper.toolCallPayload(name, query)));
                                })
                                .onToolExecuted(exec -> {
                                    hasEmitted.set(true);
                                    String name = exec.request().name();
                                    String result = exec.result();
                                    int count = WebSearchEventHelper.countResults(result);
                                    log.info("Tool call done [sessionId={}] {} count={}", sessionId, name, count);
                                    sink.next(new ChatEvent("tool_result", WebSearchEventHelper.toolResultPayload(name, count, result)));
                                })
                                .onError(sink::error)
                                .onCompleteResponse(r -> {
                                    log.info("Stream completed [sessionId={}]", sessionId);
                                    sink.complete();
                                })
                                .start()
                )
                .retryWhen(Retry.backoff(retryProperties.getLlm().getMaxAttempts(), retryProperties.getLlm().getMinBackoff())
                        .maxBackoff(retryProperties.getLlm().getMaxBackoff())
                        .filter(err -> !hasEmitted.get() && RetryPolicy.isRetryable(err))
                        .doBeforeRetry(rs -> log.warn("LLM retry #{} [sessionId={}] cause={}",
                                rs.totalRetries() + 1, sessionId, rs.failure().toString())))
                .onErrorResume(err -> {
                    log.error("Stream error [sessionId={}]", sessionId, err);
                    return Flux.just(new ChatEvent("error", err.getMessage() != null ? err.getMessage() : "AI 服务异常，请稍后重试"));
                })
                .doOnCancel(() -> log.info("Stream cancelled [sessionId={}]", sessionId));
    }

    public void clearSession(String sessionId) {
        log.info("Clearing session memory: {}", sessionId);
        chatMemoryStore.deleteMessages(sessionId);
    }
}
