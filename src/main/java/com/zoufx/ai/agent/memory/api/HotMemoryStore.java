package com.zoufx.ai.agent.memory.api;

import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Hot Memory：结构化关键事实存储（hot_memory 表）。
 *
 * 定位：与 Memory Stream（Cold Archive）==并行==的两条独立写入路径，不互相派生：
 * - Cold 自动 append 每轮对话原文
 * - Hot 由 LLM 通过 @Tool（{@code update_hot_memory}）主动晶化结构化字段写入
 *
 * v1 启用 key 集合：{@code display_name}（解决 v0 身份"短期记忆"病灶）。
 * v2 会扩展到 language / timezone / role 等。
 *
 * ==读/写接口签名风格不一致的设计取舍==：
 * - {@link #get(String, String)} / {@link #snapshot(String)} ==同步==：调用方是
 *   {@link com.zoufx.ai.agent.config.SystemPromptComposer#compose}，由 LC4J 在
 *   WebFlux event loop 上同步内联调用（{@code Function<Object, String>} 契约），
 *   无法 {@code .block()} 等 Mono——见 MemoryStoreContract.isEmpty 的同款例外
 * - {@link #set(String, String, String)} 反应式：调用方是 @Tool 方法（LC4J 工具线程），
 *   {@code .block()} 桥接合规；保持 Mono 签名为未来在 event loop 上写入（v2 后台任务等）留口子
 */
public interface HotMemoryStore {

    /**
     * 同步读单个 key。实现侧用 PK 单点查询，开销可忽略。
     * 返回 {@link Optional#empty()} 表示该 user / key 未写入过。
     */
    Optional<String> get(String userId, String key);

    /**
     * 同步一次性读取该 userId 下全部已写入的 key。供 v2 多字段 Hot 注入使用，v1 暂不用。
     */
    Map<String, String> snapshot(String userId);

    /**
     * 反应式写入：UPSERT 语义，后写覆盖前写。
     * 调用方在 @Tool 线程，会用 {@code .block()} 桥接。
     */
    Mono<Void> set(String userId, String key, String value);
}
