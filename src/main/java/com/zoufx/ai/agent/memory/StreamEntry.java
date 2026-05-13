package com.zoufx.ai.agent.memory;

/**
 * Memory Stream（Cold Archive）的检索返回 DTO。
 *
 * v1 字段最小集：足够 session_search 工具回填上下文用。
 * v2 引入 importance / embedding 后再扩列。
 */
public record StreamEntry(long id, String role, String content, long createdAt) {}
