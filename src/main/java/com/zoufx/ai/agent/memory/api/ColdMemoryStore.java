package com.zoufx.ai.agent.memory.api;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;
import com.zoufx.ai.agent.memory.model.ColdMemoryEntry;

import java.util.List;

/**
 * 冷内存（Cold Archive）业务接口——经历流存储。
 *
 * 与 {@link ChatMemoryStore}（工作记忆，继承 LC4J ChatMemoryStore）并行：
 * - {@link ChatMemoryStore}：滑窗 20 条，全量替换语义
 * - {@link ColdMemoryStore}：所有用户/AI 消息按时间序==只追加==，无上限
 *
 * 写入路径为 Controller/Service-driven Append（不在 LC4J Hook 里做，避免与 LC4J 全量替换语义冲突）：
 * - {@code ChatService.beforeStream()} 接到请求 → append user prompt
 * - {@code ChatService.onStreamComplete()} 流式响应拼装完 → append assistant text
 *
 * 当前不写入 tool_result（噪音大）。
 *
 * 所有方法均为反应式签名——调用方都在 WebFlux 反应式链（Service）或 LC4J 工具线程
 * （ColdMemorySearchTool 内部可 .block() 桥接）；不与 LC4J SystemPromptProvider 同步契约挂钩，
 * 因此整接口可以全反应式（对比 {@link HotMemoryStore} 的 snapshot 必须同步）。
 */
public interface ColdMemoryStore {

    /**
     * 追加一条经历流记录。失败应仅记日志不抛错（不阻断主对话流）。
     *
     * @param role         'user' / 'assistant'（当前写入这两类）
     * @param metadataJson JSON 字符串，当前留空（预留 importance/tags/embedding_id）
     * @param mood         AI 回复时的情绪关键词（参见 {@code Moods.ALL}），仅 assistant 消息有值；user 消息传 null
     */
    Mono<Void> append(String userId, String role, String content, String metadataJson, @Nullable String mood);

    /**
     * FTS5 全文检索经历流。必须带 userId 过滤防跨用户泄露。
     *
     * @param limit 调用方期望条数，实现侧应做上界裁剪（建议默认 5、上限 20）
     */
    Mono<List<ColdMemoryEntry>> search(String userId, String keyword, int limit);
}
