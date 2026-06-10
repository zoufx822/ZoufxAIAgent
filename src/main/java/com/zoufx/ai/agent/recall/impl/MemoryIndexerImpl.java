package com.zoufx.ai.agent.recall.impl;

import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.support.HotMemoryType;
import com.zoufx.ai.agent.recall.api.ImportanceScorer;
import com.zoufx.ai.agent.recall.api.MemoryIndexer;
import com.zoufx.ai.agent.recall.support.MemoryVectorMeta;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * {@link MemoryIndexer} 的 Qdrant 实现。
 *
 * <p>写入 = embed(content) → 组装 {@link TextSegment}（==空正文：用 sourceId 占位、不放 content==）
 * + 6 个指针元数据 → {@code addAll(id, emb, seg)}。pointId 确定性派生保证幂等/覆盖。
 * 全程阻塞调用包在 {@code boundedElastic}，失败 onErrorResume 吞为 empty（不阻断主流）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryIndexerImpl implements MemoryIndexer {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ImportanceScorer importanceScorer;
    private final HotMemoryStore hotMemoryStore;

    @Override
    public Mono<Void> index(String userId, String memType, String sourceId, String content,
                            @Nullable String role, long createdAt) {
        return Mono.<Void>fromRunnable(() -> {
                    Embedding emb = embeddingModel.embed(content).content();
                    store(userId, memType, sourceId, content, role, createdAt, emb);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(err -> swallow(memType, sourceId, err));
    }

    @Override
    public Mono<Void> indexWith(String userId, String memType, String sourceId, String content,
                                @Nullable String role, long createdAt, Embedding embedding) {
        return Mono.<Void>fromRunnable(() -> store(userId, memType, sourceId, content, role, createdAt, embedding))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(err -> swallow(memType, sourceId, err));
    }

    private void store(String userId, String memType, String sourceId, String content,
                       @Nullable String role, long createdAt, Embedding emb) {
        String username = hotMemoryStore.snapshot(userId, HotMemoryType.USER_IMPRESSION).get("username");
        double importance = importanceScorer.score(memType, role, content, username);
        Metadata md = new Metadata();
        md.put(MemoryVectorMeta.USER_ID, userId);
        md.put(MemoryVectorMeta.MEM_TYPE, memType);
        md.put(MemoryVectorMeta.SOURCE_ID, sourceId);
        md.put(MemoryVectorMeta.CREATED_AT, createdAt);
        md.put(MemoryVectorMeta.IMPORTANCE, importance);
        if (role != null && !role.isBlank()) md.put(MemoryVectorMeta.ROLE, role);

        // 空正文：text 字段放 sourceId 占位（非内容），正文留 SQLite 正本
        TextSegment seg = TextSegment.from(sourceId, md);
        String pid = pointId(userId, memType, sourceId);
        embeddingStore.addAll(List.of(pid), List.of(emb), List.of(seg));
        log.debug("Indexed vector [userId={}] memType={} sourceId={} importance={}", userId, memType, sourceId, importance);
    }

    private Mono<Void> swallow(String memType, String sourceId, Throwable err) {
        log.warn("MemoryIndexer failed [memType={} sourceId={}]: {}", memType, sourceId, err.toString());
        return Mono.empty();
    }

    /** 确定性 point id = UUIDv3(userId|memType|sourceId)。 */
    static String pointId(String userId, String memType, String sourceId) {
        String key = userId + "|" + memType + "|" + sourceId;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
