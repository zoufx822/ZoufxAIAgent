package com.zoufx.ai.agent.memory.model;

import org.jspecify.annotations.Nullable;

/**
 * 冷内存（ColdMemoryDao）中的单条记录。
 *
 * <p>{@code mood} 仅 assistant 消息可能有值——LLM 在回复转折处追加的
 * {@code ⟦mood:KEYWORD⟧} 由 {@code MoodEventProcessor} 提取并随 cold_memory 一同持久化。
 * user 消息无 mood，恒为 null。
 */
public record ColdMemory(long id, String role, String content,
                              @Nullable String mood, long createdAt) {}
