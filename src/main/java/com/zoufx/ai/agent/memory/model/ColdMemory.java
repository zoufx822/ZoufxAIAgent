package com.zoufx.ai.agent.memory.model;

import org.jspecify.annotations.Nullable;

/**
 * 冷内存（ColdMemoryDao）中的单条记录。
 *
 * <p>{@code emotion} / {@code intensity} 记录这条消息对应的用户情绪（1–5）——
 * 情绪阶段由分类器写入，当前阶段恒为 null。assistant 行可空。
 */
public record ColdMemory(long id, String role, String content,
                              @Nullable String emotion, @Nullable Integer intensity, long createdAt) {}
