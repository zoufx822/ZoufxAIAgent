package com.zoufx.ai.agent.memory.api;

import dev.langchain4j.data.message.ChatMessage;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 记忆存储业务接口（反应式）。
 *
 * LC4J 自带的 {@link dev.langchain4j.store.memory.chat.ChatMemoryStore} 是技术接口，
 * key 类型是 {@code Object memoryId}；这里抽一层窄接口面向业务（{@code String userId}），
 * 方便后续扩展 recall / search / 分类型查询，且业务代码绑接口不绑实现。
 *
 * v0.1 起所有方法返回 {@code Mono<T>}：阻塞 JDBC 由实现层 {@code boundedElastic} 包装下沉，
 * 调用方不再手写 {@code Mono.fromRunnable(...).subscribeOn(boundedElastic())} 胶水。
 *
 * 三类调用上下文：
 * - WebFlux event loop（Controller/Service）：直接 chain，不阻塞
 * - LC4J 框架线程（SystemPromptProvider / @Tool 内部）：可 {@code .block()} 同步桥接
 */
public interface MemoryStore {

    Mono<List<ChatMessage>> loadByUserId(String userId);

    Mono<Void> saveByUserId(String userId, List<ChatMessage> messages);

    Mono<Void> deleteByUserId(String userId);

    /**
     * 清理孤儿 tool 消息——AiMessage(tool_calls) 没对应 ToolExecutionResultMessage，反之亦然。
     * <p>触发场景：用户在 LC4J 调用工具期间按下前端 stop 按钮，导致 chat_memory 残留半成品消息序列。
     * 由 {@link com.zoufx.ai.agent.chat.service.AIChatService#beforeStream} 在每次请求入口调一次，
     * 在 LC4J 接管 ChatMemoryStore 之前持久化清理，让 LC4J 内部 add 流程不受 sanitize 干扰。
     * <p>返回是否真的清理了内容（仅用于日志）。
     */
    Mono<Boolean> cleanupOrphans(String userId);

    /**
     * 用于"陌生人识别"——记忆为空 = AI 不认识此人。
     *
     * ==同步签名（异常方法）==：唯一调用方是 {@link com.zoufx.ai.agent.config.SystemPromptComposer#compose}，
     * 它作为 LC4J SystemPromptProvider 被同步内联调用在 WebFlux event loop 上，
     * 无法等待 Mono；改用 .block() 会被 Reactor NonBlockingHook 拦下。
     *
     * 实现侧用 {@code SELECT EXISTS} 单次 PK 查询，开销可忽略，event loop 上同步执行可接受。
     */
    boolean isEmpty(String userId);
}
