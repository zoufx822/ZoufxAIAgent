package com.zoufx.ai.agent.service.adapter;

import com.zoufx.ai.agent.assistant.ChatAssistant;
import com.zoufx.ai.agent.model.ChatEvent;
import com.zoufx.ai.agent.util.WebSearchEventHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 把 LC4J {@code TokenStream} 的 6 个回调（thinking / content / tool_call / tool_result / error / complete）
 * 桥接为 Reactor {@link Flux} 上的领域事件 {@link ChatEvent}。
 *
 * 设计目标：让 {@code AIChatService} 不再直接依赖 LC4J SDK 的链式 API，只面向 {@code Flux<ChatEvent>} 编程。
 *
 * 注意：
 * - {@code hasEmitted} 由调用方持有并传入——它同时被重试 filter 读取，决定"流是否已开始"
 * - SSE 事件载荷的 JSON 拼装委托给 {@link WebSearchEventHelper}，本类只负责事件类型路由
 * - LC4J 的 {@code .start()} 在框架自己的线程上推送事件，与 WebFlux event loop 隔离
 */
@Slf4j
@Component
public class TokenStreamToFluxAdapter {

    /**
     * 订阅 LC4J TokenStream 并把回调流翻译为 {@code Flux<ChatEvent>}。
     *
     * @param assistant   选定模式（thinking / nonThinking）的 ChatAssistant
     * @param userId      LC4J @MemoryId
     * @param prompt      用户输入
     * @param hasEmitted  共享状态：流首次推送任何事件后置 true（重试策略据此决定是否重试）
     */
    public Flux<ChatEvent> bridge(ChatAssistant assistant,
                                  String userId,
                                  String prompt,
                                  AtomicBoolean hasEmitted) {
        return Flux.create(sink ->
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
        );
    }
}
