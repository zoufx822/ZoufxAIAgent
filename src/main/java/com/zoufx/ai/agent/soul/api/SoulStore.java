package com.zoufx.ai.agent.soul.api;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * SOUL Store——AI 自身人格的全局单例存储（无 userId 维度）。
 *
 * <p>与 {@link HotMemoryStore}（按 userId 存储用户认知）对偶。每次请求开始时由
 * {@code SystemPromptComposer} 读取 snapshot 注入 system prompt。
 * 不暴露写工具给 LLM（AI 不能自己改自己的人格），{@link #set} 保留给运维接口。
 *
 * <p>读/写签名分裂原因同 {@link HotMemoryStore}：get/snapshot 同步（event loop），
 * set 反应式（boundedElastic）。
 */
public interface SoulStore {

    /** 同步一次性读取全部已写入的 key。compose 注入用。 */
    Map<String, String> snapshot();

    /** 反应式写入：UPSERT 语义。管理 API 在反应式 chain 上调用。 */
    Mono<Void> set(String key, String value);
}
