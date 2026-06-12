package com.zoufx.ai.agent.memory.api;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 业务侧 ChatMemoryDao——继承 LC4J {@code dev.langchain4j.store.memory.chat.ChatMemoryStore}
 * 同步契约（按 Object memoryId 给框架调），叠加反应式业务方法（按 String anchorId 给业务调）。
 * 实现类只实现本接口，同一 Bean 同时满足框架注入和业务注入。
 *
 * <p>save 时需把 user_id 列也写入（冗余兜底）——由实现侧通过 {@link AnchorMemoryDao#findUserId}
 * 反查。anchorId 不存在视为异常状态，fail-fast。
 *
 * <p>反应式方法返回 Mono，阻塞 JDBC 在 boundedElastic 调度。
 */
public interface ChatMemoryDao extends ChatMemoryStore {

    /** 同步加载某锚点全部消息（按写入顺序）——供已在 boundedElastic 上的阻塞流水线使用。 */
    List<ChatMessage> loadByAnchorId(String anchorId);

    Mono<List<ChatMessage>> loadByAnchorIdAsync(String anchorId);

    /**
     * 清理孤儿 tool 消息——AiMessage(tool_calls) 没对应 ToolExecutionResultMessage，反之亦然。
     * <p>触发场景：用户 stop 中断流，LC4J 工具调用写到一半，chat_memory 残留半成品消息序列。
     * 在流取消（doOnCancel）时 fire-and-forget 调用，下次 LC4J 接管前历史已自愈。
     */
    Mono<Void> cleanupOrphansAsync(String anchorId);

    /**
     * 移除末尾孤儿 UserMessage——LC4J 在调用 LLM 前即把用户消息写入 chat_memory；
     * 若 LLM 因网络错误中断且无 AI 回复写入，该消息成为孤儿，导致下次请求携带重复历史。
     * <p>在流结束但未收到任何 AI 内容时（onStreamComplete buffer 为空）fire-and-forget 调用。
     */
    Mono<Void> removeLastOrphanUserMessageAsync(String anchorId);
}
