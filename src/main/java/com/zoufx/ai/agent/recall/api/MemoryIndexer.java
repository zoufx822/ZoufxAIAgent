package com.zoufx.ai.agent.recall.api;

import dev.langchain4j.data.embedding.Embedding;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

/**
 * 把一条记忆写入语义索引（Qdrant）。失败仅记日志不抛错（不阻断主流 / 工具返回）。
 *
 * <p>pointId 由 (userId|memType|sourceId) ==确定性派生==，无需 upsert 标志：append-only 三类
 * sourceId 唯一 → 天然不重复且 backfill 幂等；user-impression 同字段复用 → 同 id 覆盖（天然 UPSERT）。
 *
 * <p>Qdrant ==只存向量 + 指针元数据，不存正文==（content 仅用于算 embedding）。
 */
public interface MemoryIndexer {

    /**
     * importance 由索引器内部 {@code ImportanceScorer} 算（调用方无需关心评分细节）。
     *
     * @param memType  cold | significant-event | commitment | user-impression
     * @param sourceId 来源主键：cold_memory.id（字符串化）/ hot_memory key（UUID）/ impression 字段名
     * @param content  记忆文本（用于算 embedding + importance，不写进 payload）
     * @param role     cold 的 'user' / 'assistant'；hot 类传 null
     */
    Mono<Void> index(String userId, String memType, String sourceId, String content,
                     @Nullable String role, long createdAt);

    /** 已算好 embedding 时的重载——query 与 cold-user 复用同一向量，省一次 embedding；content 仅用于算 importance。 */
    Mono<Void> indexWith(String userId, String memType, String sourceId, String content,
                         @Nullable String role, long createdAt, Embedding embedding);
}
