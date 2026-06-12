package com.zoufx.ai.agent.vector.impl;

import com.zoufx.ai.agent.memory.api.ColdMemoryDao;
import com.zoufx.ai.agent.memory.api.HotMemoryDao;
import com.zoufx.ai.agent.memory.model.ColdMemory;
import com.zoufx.ai.agent.memory.support.HotMemoryType;
import com.zoufx.ai.agent.vector.api.RecallService;
import com.zoufx.ai.agent.vector.model.RecallResult;
import com.zoufx.ai.agent.vector.property.RecallProps;
import com.zoufx.ai.agent.vector.support.VectorPayload;
import com.zoufx.ai.agent.vector.support.RecallRanker;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * {@link RecallService} 实现。流水线：embed → Qdrant filter 召回 → 三维加权 → MMR → Top-N →
 * 回 SQLite 正本取正文。Qdrant 只返回向量 + 指针元数据，正文不在 Qdrant。
 *
 * <p>阻塞调用（embed / Qdrant / JDBC 回查）全在 {@code boundedElastic}；失败吞为空列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecallServiceImpl implements RecallService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RecallRanker ranker;
    private final RecallProps props;
    private final ColdMemoryDao coldMemoryDao;
    private final HotMemoryDao hotMemoryDao;

    @Override
    public List<RecallResult> recall(String userId, Embedding embedding, int limit, @Nullable Long windowSince) {
        try {
            Filter filter = metadataKey(VectorPayload.USER_ID).isEqualTo(userId);   // 用户隔离硬要求
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .filter(filter)
                    .maxResults(props.getTopK())
                    .minScore(props.getMinScore())
                    .build();

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(req).matches();
            long now = System.currentTimeMillis();

            List<Candidate> candidates = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> m : matches) {
                Metadata md = m.embedded().metadata();
                String memType = md.getString(VectorPayload.MEM_TYPE);
                String sourceId = md.getString(VectorPayload.SOURCE_ID);
                Long createdAt = md.getLong(VectorPayload.CREATED_AT);
                if (memType == null || sourceId == null || createdAt == null) continue;
                // 排除滑窗内已可见的 cold 条目（hot 不在滑窗，不过滤）
                if (windowSince != null && VectorPayload.COLD.equals(memType) && createdAt >= windowSince) continue;

                Double impBox = md.getDouble(VectorPayload.IMPORTANCE);
                double importance = impBox == null ? 0.5 : impBox;
                double relevance = m.score() == null ? 0.0 : m.score();
                double recency = ranker.recency(now - createdAt);
                double finalScore = ranker.finalScore(relevance, recency, importance);
                float[] vector = m.embedding() == null ? null : m.embedding().vector();
                candidates.add(new Candidate(memType, sourceId, createdAt, relevance, recency, importance, finalScore, vector));
            }

            candidates.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));
            List<Candidate> selected = selectTopN(candidates, Math.max(1, limit));
            List<RecallResult> results = hydrate(userId, selected);
            log.info("Recall [userId={}] matched={} selected={} hydrated={}",
                    userId, matches.size(), selected.size(), results.size());
            return results;
        } catch (Exception err) {
            log.warn("Recall failed [userId={}]: {}", userId, err.toString());
            return List.of();
        }
    }

    /** MMR 多样性选择；归一化 finalScore 后与候选间余弦相似度按 λ 混合。 */
    private List<Candidate> selectTopN(List<Candidate> ranked, int limit) {
        RecallProps.Mmr mmr = props.getMmr();
        if (!mmr.isEnabled() || ranked.size() <= limit) {
            return ranked.size() <= limit ? ranked : new ArrayList<>(ranked.subList(0, limit));
        }
        double maxScore = ranked.stream().mapToDouble(Candidate::finalScore).max().orElse(1.0);
        if (maxScore <= 0) maxScore = 1.0;
        double lambda = mmr.getLambda();

        List<Candidate> pool = new ArrayList<>(ranked);
        List<Candidate> selected = new ArrayList<>();
        while (!pool.isEmpty() && selected.size() < limit) {
            Candidate best = null;
            double bestMmr = Double.NEGATIVE_INFINITY;
            for (Candidate c : pool) {
                double maxSim = 0.0;
                for (Candidate s : selected) {
                    maxSim = Math.max(maxSim, RecallRanker.cosine(c.vector(), s.vector()));
                }
                double mmrScore = lambda * (c.finalScore() / maxScore) - (1 - lambda) * maxSim;
                if (mmrScore > bestMmr) {
                    bestMmr = mmrScore;
                    best = c;
                }
            }
            selected.add(best);
            pool.remove(best);
        }
        return selected;
    }

    /** 仅对最终选中的 Top-N 回 SQLite 正本取正文；正本缺失（已删）则跳过。 */
    private List<RecallResult> hydrate(String userId, List<Candidate> selected) {
        List<Long> coldIds = selected.stream()
                .filter(c -> VectorPayload.COLD.equals(c.memType()))
                .map(c -> parseLongOrNull(c.sourceId()))
                .filter(Objects::nonNull)
                .toList();
        Map<Long, String> coldContent = new HashMap<>();
        if (!coldIds.isEmpty()) {
            for (ColdMemory e : coldMemoryDao.fetchByIds(userId, coldIds)) {
                coldContent.put(e.id(), e.content());
            }
        }

        Map<String, Map<String, String>> hotByType = new HashMap<>();
        for (String type : HotMemoryType.ALL) {
            List<String> keys = selected.stream()
                    .filter(c -> type.equals(c.memType()))
                    .map(Candidate::sourceId)
                    .toList();
            if (!keys.isEmpty()) {
                hotByType.put(type, hotMemoryDao.fetchValues(userId, type, keys));
            }
        }

        List<RecallResult> out = new ArrayList<>();
        for (Candidate c : selected) {
            String content;
            if (VectorPayload.COLD.equals(c.memType())) {
                Long id = parseLongOrNull(c.sourceId());
                content = id == null ? null : coldContent.get(id);
            } else {
                Map<String, String> m = hotByType.get(c.memType());
                content = m == null ? null : m.get(c.sourceId());
            }
            if (content == null || content.isBlank()) continue;
            out.add(new RecallResult(c.memType(), content, c.createdAt(),
                    c.relevance(), c.recency(), c.importance(), c.finalScore()));
        }
        return out;
    }

    @Nullable
    private static Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Candidate(String memType, String sourceId, long createdAt,
                             double relevance, double recency, double importance,
                             double finalScore, float @Nullable [] vector) {}
}
