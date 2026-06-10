package com.zoufx.ai.agent.recall.model;

/**
 * 一条召回结果。{@code content} 来自 SQLite 正本（Qdrant 不存正文，按 sourceId 回查）。
 *
 * @param memType    cold / significant-event / commitment / user-impression
 * @param content    记忆原文（回查 hydration 得到）
 * @param createdAt  记忆写入时间（毫秒）
 * @param relevance  向量余弦相关性 [0,1]
 * @param recency    时近性衰减 [0,1]
 * @param importance 重要性 [0,1]
 * @param finalScore 三维加权总分
 */
public record RecallResult(String memType, String content, long createdAt,
                           double relevance, double recency, double importance, double finalScore) {}
