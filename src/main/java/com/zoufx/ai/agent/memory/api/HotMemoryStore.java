package com.zoufx.ai.agent.memory.api;

import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Hot Memory：见到此人就立刻浮现、不需检索的常驻记忆（v0.13 起分 type）。
 *
 * <p>定位：与 Memory Stream（Cold Archive）==并行==的两条独立写入路径，不互相派生：
 * <ul>
 *   <li>Cold 自动 append 每轮对话原文</li>
 *   <li>Hot 由 LLM 通过 @Tool 主动晶化结构化信息写入</li>
 * </ul>
 *
 * <p>==v0.13 引入 type 维度==：hot_memory 是父概念，user-impression / significant-event / ...
 * 是子分类。每条记录都属于某个 type，PK 为 (user_id, type, key)。
 * type 常量见 {@link HotMemoryType}。
 *
 * <p>==读/写接口签名风格不一致的设计取舍==：
 * <ul>
 *   <li>{@link #get} / {@link #snapshot} ==同步==：调用方是 PromptSection.render，由 LC4J 在
 *     WebFlux event loop 上同步内联调用，无法 .block() 等 Mono</li>
 *   <li>{@link #set} 反应式：调用方是 @Tool 方法（LC4J 工具线程），.block() 桥接合规；
 *     保持 Mono 签名为未来在 event loop 上写入留口子</li>
 * </ul>
 */
public interface HotMemoryStore {

    /**
     * 同步读单个 key。实现侧用 PK 单点查询，开销可忽略。
     * 返回 {@link Optional#empty()} 表示该 (user / type / key) 未写入过。
     */
    Optional<String> get(String userId, String type, String key);

    /**
     * 同步一次性读取该 (userId, type) 下全部已写入的 key/value。
     * 供 IdentityPromptSection 等同类型 Section 使用。
     */
    Map<String, String> snapshot(String userId, String type);

    /**
     * 反应式写入：UPSERT 语义，后写覆盖前写。
     * 调用方在 @Tool 线程，会用 {@code .block()} 桥接。
     */
    Mono<Void> set(String userId, String type, String key, String value);
}
