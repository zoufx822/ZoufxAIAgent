package com.zoufx.ai.agent.memory;

import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * SOUL Store：AI 自身人格 / 说话风格 / 价值观 / 口头禅（v1.1）。
 *
 * <p>与 {@link HotMemoryStoreContract}（UserProfile）的对偶：
 * <ul>
 *   <li>HotMemoryStoreContract 是 AI 对 "用户" 的认知（每个 userId 一份）</li>
 *   <li>SoulStoreContract 是 AI 对 "自己" 的人格（==全局单例，无 userId 维度==）</li>
 * </ul>
 *
 * <p>由 {@code SystemPromptComposer.compose()} 在每次请求开头读 snapshot 注入 system prompt。
 * 修改入口仅 {@code SoulController.PUT /admin/soul/{key}}——==不暴露写工具给 LLM==
 * （AI 不能自己改自己的人格）。
 *
 * <p>读/写接口签名分裂规则同 {@link HotMemoryStoreContract}：get / snapshot 同步（compose 在 event loop
 * 上不能 .block()），set 反应式（管理 API 在反应式 chain 上调用）。
 */
public interface SoulStoreContract {

    /** 同步读单个 key。返回 {@link Optional#empty()} 表示该 key 未被 seed 也未被管理 API 写入过。 */
    Optional<String> get(String key);

    /** 同步一次性读取全部已写入的 key。compose 注入用。 */
    Map<String, String> snapshot();

    /** 反应式写入：UPSERT 语义。管理 API 在反应式 chain 上调用。 */
    Mono<Void> set(String key, String value);
}
