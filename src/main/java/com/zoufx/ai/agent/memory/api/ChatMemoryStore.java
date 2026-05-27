package com.zoufx.ai.agent.memory.api;

import dev.langchain4j.data.message.ChatMessage;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 业务侧 ChatMemoryStore——继承 LC4J {@code dev.langchain4j.store.memory.chat.ChatMemoryStore}
 * 同步契约（按 Object memoryId 给框架调），叠加反应式业务方法（按 String anchorId 给业务调）。
 * 实现类只实现本接口，同一 Bean 同时满足框架注入和业务注入。
 *
 * <p>save 时需把 user_id 列也写入（冗余兜底）——由实现侧通过 {@link AnchorMemoryStore#findUserId}
 * 反查。anchorId 不存在视为异常状态，fail-fast。
 *
 * <p>反应式方法返回 Mono，阻塞 JDBC 在 boundedElastic 调度。
 */
public interface ChatMemoryStore extends dev.langchain4j.store.memory.chat.ChatMemoryStore {

    Mono<List<ChatMessage>> loadByAnchorId(String anchorId);

    Mono<Void> saveByAnchorId(String anchorId, List<ChatMessage> messages);

    Mono<Void> deleteByAnchorId(String anchorId);

    /**
     * 清理孤儿 tool 消息——AiMessage(tool_calls) 没对应 ToolExecutionResultMessage，反之亦然。
     * <p>触发场景：用户在 LC4J 调用工具期间按下前端 stop 按钮，导致 chat_memory 残留半成品消息序列。
     * 由 {@link com.zoufx.ai.agent.chat.service.ChatService#beforeStream} 在每次请求入口调一次，
     * 在 LC4J 接管前持久化清理，让 LC4J 内部 add 流程不受 sanitize 干扰。
     * <p>返回是否真的清理了内容（仅用于日志）。
     */
    Mono<Boolean> cleanupOrphans(String anchorId);
}
