package com.zoufx.ai.agent.memory.api;

import reactor.core.publisher.Mono;
import com.zoufx.ai.agent.memory.model.ColdMemoryEntry;

import java.util.List;

/**
 * 冷内存（Cold Archive）业务接口——经历流存储。
 *
 * 与 {@link MemoryStore}（工作记忆，LC4J ChatMemoryStore 载体）并行：
 * - {@link MemoryStore}：滑窗 20 条，全量替换语义
 * - {@link ColdMemoryStore}：所有用户/AI 消息按时间序==只追加==，无上限
 *
 * 写入路径为 Controller-driven Append（不在 LC4J Hook 里做，避免与 LC4J 全量替换语义冲突）：
 * - {@code AIChatController.chat()} 接到请求 → append user prompt
 * - {@code AIChatService.chat()} 流式响应拼装完 → append assistant text
 *
 * v1 范围：tool_result 不进 stream（噪音大，等 v3 Consolidation 决策）。
 *
 * 所有方法均为反应式签名——调用方都在 WebFlux event loop（Controller / Service）或 LC4J 工具线程
 * （SessionSearchTool 内部可 .block() 桥接）；不与 LC4J SystemPromptProvider 同步契约挂钩，
 * 因此整接口可以全反应式（对比 {@link HotMemoryStore} 的 get/snapshot 必须同步）。
 */
public interface ColdMemoryStore {

    /**
     * 追加一条经历流记录。失败应仅记日志不抛错（不阻断主对话流）。
     *
     * @param role         'user' / 'assistant' / 'tool'（v1 暂不写）/ 'reflection'（v3 预留）
     * @param metadataJson JSON 字符串，v1 留空；v2 写入 importance/tags/embedding_id
     */
    Mono<Void> append(String userId, String role, String content, String metadataJson);

    /**
     * FTS5 全文检索经历流。必须带 userId 过滤防跨用户泄露。
     *
     * @param limit 调用方期望条数，实现侧应做上界裁剪（建议默认 5、上限 20）
     */
    Mono<List<ColdMemoryEntry>> search(String userId, String keyword, int limit);

    /**
     * 调试用：拉最近 N 条经历。生产路径不调用。
     */
    Mono<List<ColdMemoryEntry>> loadRecent(String userId, int limit);
}
