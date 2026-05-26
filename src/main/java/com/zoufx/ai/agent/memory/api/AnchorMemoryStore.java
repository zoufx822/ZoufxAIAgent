package com.zoufx.ai.agent.memory.api;

import dev.langchain4j.data.message.ChatMessage;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 锚点存储业务接口（反应式）——管理 anchor_memory 表，按 anchorId 隔离对话消息。
 *
 * <p>与 LC4J {@link dev.langchain4j.store.memory.chat.ChatMemoryStore}（技术接口）分工：
 * ChatMemoryStore 由框架按 Object memoryId 调，本接口面向业务按 String userId/anchorId 调。
 * 实现类 {@code SqliteAnchorMemoryStore} 双接口共用一套 JDBC 通路。
 *
 * <p>所有方法返回 Mono，阻塞 JDBC 在 boundedElastic 调度。
 */
public interface AnchorMemoryStore {

    Mono<List<ChatMessage>> loadByUserId(String userId);

    Mono<Void> saveByUserId(String userId, List<ChatMessage> messages);

    Mono<Void> deleteByUserId(String userId);

    /**
     * 清理孤儿 tool 消息——AiMessage(tool_calls) 没对应 ToolExecutionResultMessage，反之亦然。
     * <p>触发场景：用户在 LC4J 调用工具期间按下前端 stop 按钮，导致 anchor_memory 残留半成品消息序列。
     * 由 {@link com.zoufx.ai.agent.chat.service.ChatService#beforeStream} 在每次请求入口调一次，
     * 在 LC4J 接管 ChatMemoryStore 之前持久化清理，让 LC4J 内部 add 流程不受 sanitize 干扰。
     * <p>返回是否真的清理了内容（仅用于日志）。
     */
    Mono<Boolean> cleanupOrphans(String userId);
}
