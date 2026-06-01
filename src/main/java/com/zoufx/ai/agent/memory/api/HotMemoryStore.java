package com.zoufx.ai.agent.memory.api;

import com.zoufx.ai.agent.memory.model.HotMemoryEntry;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Hot Memory 存储契约——见到此人就立刻浮现、不需检索的常驻记忆。
 *
 * <p>与 Memory Stream（Cold Archive）并行写入，不互相派生：Hot 由 LLM 通过 {@code @Tool} 主动晶化，
 * Cold 自动 append 每轮对话原文。
 *
 * <p><b>读/写签名分裂原因</b>：读方法（{@link #get}/{@link #snapshot}/{@link #recent}）同步——
 * 调用方 {@code PromptSection.render} 在 WebFlux event loop 上执行，无法 {@code .block()}；
 * 写方法（{@link #set}）反应式——调用方是 {@code @Tool} 方法，在工具线程上可 {@code .block()} 桥接。
 */
public interface HotMemoryStore {

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

    /**
     * 同步读该 (userId, type) 下最近 N 条记录，按 {@code updated_at DESC} 排序。
     * 供 significant-event / commitment 等 append-only type 的 Section 注入用。
     */
    List<HotMemoryEntry> recent(String userId, String type, int limit);
}
