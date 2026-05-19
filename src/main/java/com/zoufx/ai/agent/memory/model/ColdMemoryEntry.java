package com.zoufx.ai.agent.memory.model;

/**
 * 冷内存（ColdMemoryStore）中的单条记录。
 *
 * v1 字段最小集：足够 search_cold_memory 工具回填上下文用。
 * v2 引入 importance / embedding 后再扩列。
 */
public record ColdMemoryEntry(long id, String role, String content, long createdAt) {}
